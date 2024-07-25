/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.burningman;

import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.SegwitAddress;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.dao.burningman.DelayedPayoutTxReceiverService.ReceiverFlag.BUGFIX_6699;
import static bisq.core.dao.burningman.DelayedPayoutTxReceiverService.ReceiverFlag.PRECISE_FEES;
import static bisq.core.dao.burningman.DelayedPayoutTxReceiverService.ReceiverFlag.PROPOSAL_412;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Used in the trade protocol for creating and verifying the delayed payout transaction.
 * Requires to be deterministic.
 * Changes in the parameters related to the receivers list could break verification of the peers
 * delayed payout transaction in case not both are using the same version.
 */
@Slf4j
@Singleton
public class DelayedPayoutTxReceiverService implements DaoStateListener {
    // Activation date for bugfix of receiver addresses getting overwritten by a new compensation
    // requests change address.
    // See: https://github.com/bisq-network/bisq/issues/6699
    public static final Date BUGFIX_6699_ACTIVATION_DATE = Utilities.getUTCDate(2023, GregorianCalendar.JULY, 24);
    // See: https://github.com/bisq-network/proposals/issues/412
    public static final Date PROPOSAL_412_ACTIVATION_DATE = Utilities.getUTCDate(2024, GregorianCalendar.MAY, 1);

    // We don't allow to get further back than 767950 (the block height from Dec. 18th 2022).
    private static final int MIN_SNAPSHOT_HEIGHT = Config.baseCurrencyNetwork().isRegtest() ? 0 : 767950;

    // One part of the limit for the min. amount to be included in the DPT outputs.
    // The miner fee rate multiplied by 2 times the output size is the other factor.
    // The higher one of both is used. 1000 sat is about 2 USD @ 20k price.
    private static final long DPT_MIN_OUTPUT_AMOUNT = 1000;

    // If at DPT there is some leftover amount due to capping of some receivers (burn share is
    // max. ISSUANCE_BOOST_FACTOR times the issuance share) we send it to legacy BM if it is larger
    // than DPT_MIN_REMAINDER_TO_LEGACY_BM, otherwise we spend it as miner fee.
    // 25000 sat is about 5 USD @ 20k price. We use a rather high value as we want to avoid that the legacy BM
    // gets still payouts.
    private static final long DPT_MIN_REMAINDER_TO_LEGACY_BM = 25000;

    // Min. fee rate for DPT. If fee rate used at take offer time was higher we use that.
    // We prefer a rather high fee rate to not risk that the DPT gets stuck if required fee rate would
    // spike when opening arbitration.
    private static final long DPT_MIN_TX_FEE_RATE = 10;

    // The DPT weight (= 4 * size) without any outputs.
    private static final long DPT_MIN_WEIGHT = 426;
    private static final long UNSIGNED_DPT_MIN_WEIGHT = 204;

    private final DaoStateService daoStateService;
    private final BurningManService burningManService;
    private int currentChainHeight;

    @Inject
    public DelayedPayoutTxReceiverService(DaoStateService daoStateService, BurningManService burningManService) {
        this.daoStateService = daoStateService;
        this.burningManService = burningManService;

        daoStateService.addDaoStateListener(this);
        daoStateService.getLastBlock().ifPresent(this::applyBlock);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        applyBlock(block);
    }

    private void applyBlock(Block block) {
        currentChainHeight = block.getHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum ReceiverFlag {
        BUGFIX_6699(BUGFIX_6699_ACTIVATION_DATE),
        PROPOSAL_412(PROPOSAL_412_ACTIVATION_DATE),
        PRECISE_FEES(Version.PROTOCOL_5_ACTIVATION_DATE);

        private final Date activationDate;

        ReceiverFlag(Date activationDate) {
            this.activationDate = activationDate;
        }

        public static Set<ReceiverFlag> flagsActivatedBy(Date date) {
            Set<ReceiverFlag> flags = EnumSet.allOf(ReceiverFlag.class);
            flags.removeIf(flag -> !date.after(flag.activationDate));
            return flags;
        }
    }

    // We use a snapshot blockHeight to avoid failed trades in case maker and taker have different block heights.
    // The selection is deterministic based on DAO data.
    // The block height is the last mod(10) height from the range of the last 10-20 blocks (139 -> 120; 140 -> 130, 141 -> 130).
    // We do not have the latest dao state by that but can ensure maker and taker have the same block.
    public int getBurningManSelectionHeight() {
        return getSnapshotHeight(daoStateService.getGenesisBlockHeight(), currentChainHeight);
    }

    public List<Tuple2<Long, String>> getReceivers(int burningManSelectionHeight,
                                                   long inputAmount,
                                                   long tradeTxFee) {
        return getReceivers(burningManSelectionHeight, inputAmount, tradeTxFee, ReceiverFlag.flagsActivatedBy(new Date()));
    }

    public List<Tuple2<Long, String>> getReceivers(int burningManSelectionHeight,
                                                   long inputAmount,
                                                   long tradeTxFee,
                                                   Set<ReceiverFlag> receiverFlags) {
        return getReceivers(burningManSelectionHeight, inputAmount, tradeTxFee, receiverFlags.contains(PRECISE_FEES) ?
                DPT_MIN_WEIGHT : UNSIGNED_DPT_MIN_WEIGHT, receiverFlags);
    }

    public List<Tuple2<Long, String>> getReceivers(int burningManSelectionHeight,
                                                   long inputAmount,
                                                   long tradeTxFee,
                                                   long minTxWeight,
                                                   Set<ReceiverFlag> receiverFlags) {
        checkArgument(burningManSelectionHeight >= MIN_SNAPSHOT_HEIGHT, "Selection height must be >= " + MIN_SNAPSHOT_HEIGHT);

        boolean isBugfix6699Activated = receiverFlags.contains(BUGFIX_6699);
        boolean isProposal412Activated = receiverFlags.contains(PROPOSAL_412);
        boolean usePreciseFees = receiverFlags.contains(PRECISE_FEES);

        Collection<BurningManCandidate> burningManCandidates = burningManService.getActiveBurningManCandidates(burningManSelectionHeight,
                !isProposal412Activated);
        String lbmAddress = burningManService.getLegacyBurningManAddress(burningManSelectionHeight);

        // We need to use the same txFeePerVbyte value for both traders.
        // We use the tradeTxFee value which is calculated from the average of taker fee tx size and deposit tx size.
        // Otherwise, we would need to sync the fee rate of both traders.
        // In case of very large taker fee tx we would get a too high fee, but as fee rate is anyway rather
        // arbitrary and volatile we are on the safer side. The delayed payout tx is published long after the
        // take offer event and the recommended fee at that moment might be very different to actual
        // recommended fee. To avoid that the delayed payout tx would get stuck due too low fees we use a
        // min. fee rate of 10 sat/vByte.

        // Deposit tx has a clearly defined structure, so we know the size. It is only one optional output if range amount offer was taken.
        // Smallest tx size is 246. With additional change output we add 32. To be safe we use the largest expected size.
        double txSize = 278;
        long txFeePerVbyte = Math.max(DPT_MIN_TX_FEE_RATE, Math.round(tradeTxFee / txSize));

        if (burningManCandidates.isEmpty()) {
            // If there are no compensation requests (e.g. at dev testing) we fall back to the legacy BM
            long spendableAmount = getSpendableAmount(usePreciseFees ? outputSize(lbmAddress) : 32, inputAmount, txFeePerVbyte, minTxWeight);
            return spendableAmount > DPT_MIN_REMAINDER_TO_LEGACY_BM
                    ? List.of(new Tuple2<>(spendableAmount, burningManService.getLegacyBurningManAddress(burningManSelectionHeight)))
                    : List.of();
        }

        int totalOutputSize = usePreciseFees ? burningManCandidates.stream()
                .mapToInt(candidate -> outputSize(candidate.getReceiverAddress(isBugfix6699Activated).orElseThrow()))
                .sum() : burningManCandidates.size() * 32;
        long spendableAmount = getSpendableAmount(totalOutputSize, inputAmount, txFeePerVbyte, minTxWeight);
        // We only use outputs >= 1000 sat or at least 2 times the cost for the output (32 bytes, assuming P2SH).
        // If we remove outputs it will be distributed to the remaining receivers.
        long minOutputAmount = Math.max(DPT_MIN_OUTPUT_AMOUNT, txFeePerVbyte * 32 * 2);
        // Sanity check that max share of a non-legacy BM is 20% over MAX_BURN_SHARE (taking into account potential increase due adjustment)
        long maxOutputAmount = Math.round(spendableAmount * (BurningManService.MAX_BURN_SHARE * 1.2));
        // We accumulate small amounts which gets filtered out and subtract it from 1 to get an adjustment factor
        // used later to be applied to the remaining burningmen share.
        // TODO: This still isn't completely precise. As every small output is filtered, the burn share threshold for
        //  small outputs decreases slightly. We should really use a priority queue and filter out candidates from
        //  smallest to biggest share until all the small outputs are removed. (Also, rounding errors can lead to the
        //  final fee being out by a few sats, if the burn shares add up to 100% with no balance going to the LBM.)
        long[] adjustedSpendableAmount = {spendableAmount};
        double shareAdjustment = 1 - burningManCandidates.stream()
                .mapToDouble(candidate -> {
                    double cappedBurnAmountShare = candidate.getCappedBurnAmountShare();
                    long amount = Math.round(cappedBurnAmountShare * spendableAmount);
                    if (usePreciseFees && amount < minOutputAmount) {
                        String address = candidate.getReceiverAddress(isBugfix6699Activated).orElseThrow();
                        adjustedSpendableAmount[0] += outputSize(address) * txFeePerVbyte;
                    }
                    return amount < minOutputAmount ? cappedBurnAmountShare : 0d;
                })
                .sum();

        List<Tuple2<Long, String>> receivers = burningManCandidates.stream()
                .filter(candidate -> !usePreciseFees || Math.round(candidate.getCappedBurnAmountShare() * spendableAmount) >= minOutputAmount)
                .map(candidate -> {
                    double cappedBurnAmountShare = candidate.getCappedBurnAmountShare() / shareAdjustment;
                    return new Tuple2<>(Math.round(cappedBurnAmountShare * adjustedSpendableAmount[0]),
                            candidate.getReceiverAddress(isBugfix6699Activated).orElseThrow());
                })
                .filter(tuple -> tuple.first >= minOutputAmount)
                .filter(tuple -> tuple.first <= maxOutputAmount)
                .sorted(Comparator.<Tuple2<Long, String>, Long>comparing(tuple -> tuple.first)
                        .thenComparing(tuple -> tuple.second))
                .collect(Collectors.toList());
        long totalOutputValue = receivers.stream().mapToLong(e -> e.first).sum();
        if (usePreciseFees) {
            adjustedSpendableAmount[0] -= outputSize(lbmAddress) * txFeePerVbyte;
        }
        if (totalOutputValue < adjustedSpendableAmount[0]) {
            long available = adjustedSpendableAmount[0] - totalOutputValue;
            // If the available is larger than DPT_MIN_REMAINDER_TO_LEGACY_BM we send it to legacy BM
            // Otherwise we use it as miner fee
            if (available > DPT_MIN_REMAINDER_TO_LEGACY_BM) {
                receivers.add(new Tuple2<>(available, lbmAddress));
            }
        }
        return receivers;
    }

    private static long getSpendableAmount(int totalOutputSize,
                                           long inputAmount,
                                           long txFeePerVbyte,
                                           long minTxWeight) {
        // P2SH output size: 32 bytes
        // Unsigned tx size without outputs: 51 bytes
        long txWeight = minTxWeight + totalOutputSize * 4L; // Min value: txWeight=332 (for unsigned DPT with 1 P2SH output)
        long minerFee = (txFeePerVbyte * txWeight + 3) / 4; // Min value: minerFee=830
        // We need to make sure we have at least 1000 sat as defined in TradeWalletService
        minerFee = Math.max(TradeWalletService.MIN_DELAYED_PAYOUT_TX_FEE.value, minerFee);
        return Math.max(inputAmount - minerFee, 0);
    }

    private static int outputSize(String addressString) {
        Address address = Address.fromString(Config.baseCurrencyNetworkParameters(), addressString);
        if (address instanceof LegacyAddress) {
            switch (address.getOutputScriptType()) {
                case P2PKH:
                    return 34;
                case P2SH:
                    return 32;
            }
        } else if (address instanceof SegwitAddress) {
            return ((SegwitAddress) address).getWitnessProgram().length + 11;
        }
        throw new IllegalArgumentException("Unknown output size: " + address);
    }

    private static int getSnapshotHeight(int genesisHeight, int height) {
        return getSnapshotHeight(genesisHeight, height, 10, MIN_SNAPSHOT_HEIGHT);
    }

    // Borrowed from DaoStateSnapshotService. We prefer to not reuse to avoid dependency to an unrelated domain.
    @VisibleForTesting
    static int getSnapshotHeight(int genesisHeight, int height, int grid, int minSnapshotHeight) {
        if (height > (genesisHeight + 3 * grid)) {
            int ratio = (int) Math.round(height / (double) grid);
            return Math.max(minSnapshotHeight, ratio * grid - grid);
        } else {
            return Math.max(minSnapshotHeight, genesisHeight);
        }
    }
}

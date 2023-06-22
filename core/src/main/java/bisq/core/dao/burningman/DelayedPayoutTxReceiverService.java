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

import bisq.common.config.Config;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

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

    public static boolean isBugfix6699Activated() {
        return new Date().after(BUGFIX_6699_ACTIVATION_DATE);
    }

    // We don't allow to get further back than 767950 (the block height from Dec. 18th 2022).
    static final int MIN_SNAPSHOT_HEIGHT = Config.baseCurrencyNetwork().isRegtest() ? 0 : 767950;

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

    // We use a snapshot blockHeight to avoid failed trades in case maker and taker have different block heights.
    // The selection is deterministic based on DAO data.
    // The block height is the last mod(10) height from the range of the last 10-20 blocks (139 -> 120; 140 -> 130, 141 -> 130).
    // We do not have the latest dao state by that but can ensure maker and taker have the same block.
    public int getBurningManSelectionHeight() {
        return getSnapshotHeight(daoStateService.getGenesisBlockHeight(), currentChainHeight, 10);
    }

    public List<Tuple2<Long, String>> getReceivers(int burningManSelectionHeight,
                                                   long inputAmount,
                                                   long tradeTxFee) {
        return getReceivers(burningManSelectionHeight, inputAmount, tradeTxFee, isBugfix6699Activated());
    }

    public List<Tuple2<Long, String>> getReceivers(int burningManSelectionHeight,
                                                   long inputAmount,
                                                   long tradeTxFee,
                                                   boolean isBugfix6699Activated) {
        checkArgument(burningManSelectionHeight >= MIN_SNAPSHOT_HEIGHT, "Selection height must be >= " + MIN_SNAPSHOT_HEIGHT);
        Collection<BurningManCandidate> burningManCandidates = burningManService.getActiveBurningManCandidates(burningManSelectionHeight);

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
            long spendableAmount = getSpendableAmount(1, inputAmount, txFeePerVbyte);
            return List.of(new Tuple2<>(spendableAmount, burningManService.getLegacyBurningManAddress(burningManSelectionHeight)));
        }

        long spendableAmount = getSpendableAmount(burningManCandidates.size(), inputAmount, txFeePerVbyte);
        // We only use outputs > 1000 sat or at least 2 times the cost for the output (32 bytes).
        // If we remove outputs it will be spent as miner fee.
        long minOutputAmount = Math.max(DPT_MIN_OUTPUT_AMOUNT, txFeePerVbyte * 32 * 2);
        // Sanity check that max share of a non-legacy BM is 20% over MAX_BURN_SHARE (taking into account potential increase due adjustment)
        long maxOutputAmount = Math.round(spendableAmount * (BurningManService.MAX_BURN_SHARE * 1.2));
        // We accumulate small amounts which gets filtered out and subtract it from 1 to get an adjustment factor
        // used later to be applied to the remaining burningmen share.
        double adjustment = 1 - burningManCandidates.stream()
                .filter(candidate -> candidate.getReceiverAddress(isBugfix6699Activated).isPresent())
                .mapToDouble(candidate -> {
                    double cappedBurnAmountShare = candidate.getCappedBurnAmountShare();
                    long amount = Math.round(cappedBurnAmountShare * spendableAmount);
                    return amount < minOutputAmount ? cappedBurnAmountShare : 0d;
                })
                .sum();

        List<Tuple2<Long, String>> receivers = burningManCandidates.stream()
                .filter(candidate -> candidate.getReceiverAddress(isBugfix6699Activated).isPresent())
                .map(candidate -> {
                    double cappedBurnAmountShare = candidate.getCappedBurnAmountShare() / adjustment;
                    return new Tuple2<>(Math.round(cappedBurnAmountShare * spendableAmount),
                            candidate.getReceiverAddress(isBugfix6699Activated).get());
                })
                .filter(tuple -> tuple.first >= minOutputAmount)
                .filter(tuple -> tuple.first <= maxOutputAmount)
                .sorted(Comparator.<Tuple2<Long, String>, Long>comparing(tuple -> tuple.first)
                        .thenComparing(tuple -> tuple.second))
                .collect(Collectors.toList());
        long totalOutputValue = receivers.stream().mapToLong(e -> e.first).sum();
        if (totalOutputValue < spendableAmount) {
            long available = spendableAmount - totalOutputValue;
            // If the available is larger than DPT_MIN_REMAINDER_TO_LEGACY_BM we send it to legacy BM
            // Otherwise we use it as miner fee
            if (available > DPT_MIN_REMAINDER_TO_LEGACY_BM) {
                receivers.add(new Tuple2<>(available, burningManService.getLegacyBurningManAddress(burningManSelectionHeight)));
            }
        }
        return receivers;
    }

    private static long getSpendableAmount(int numOutputs, long inputAmount, long txFeePerVbyte) {
        // Output size: 32 bytes
        // Tx size without outputs: 51 bytes
        int txSize = 51 + numOutputs * 32; // Min value: txSize=83
        long minerFee = txFeePerVbyte * txSize; // Min value: minerFee=830
        // We need to make sure we have at least 1000 sat as defined in TradeWalletService
        minerFee = Math.max(TradeWalletService.MIN_DELAYED_PAYOUT_TX_FEE.value, minerFee);
        return inputAmount - minerFee;
    }

    private static int getSnapshotHeight(int genesisHeight, int height, int grid) {
        return getSnapshotHeight(genesisHeight, height, grid, MIN_SNAPSHOT_HEIGHT);
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

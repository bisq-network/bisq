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

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    public static final int SNAPSHOT_SELECTION_GRID_SIZE = 10;

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
    @VisibleForTesting
    static final double BM_ADDRESS_LIST_SHARE_RANGE_TOLERANCE = 0.5;

    private final DaoStateService daoStateService;
    private final BurningManService burningManService;
    private final BurningManAddressListService burningManAddressListService;
    private int currentChainHeight;

    @Inject
    public DelayedPayoutTxReceiverService(DaoStateService daoStateService,
                                          BurningManService burningManService,
                                          BurningManAddressListService burningManAddressListService) {
        this.daoStateService = daoStateService;
        this.burningManService = burningManService;
        this.burningManAddressListService = burningManAddressListService;

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
        return getBurningManSelectionHeight(currentChainHeight);
    }

    public int getBurningManSelectionHeight(int chainHeight) {
        return getSnapshotHeight(daoStateService.getGenesisBlockHeight(), chainHeight,
                SNAPSHOT_SELECTION_GRID_SIZE);
    }

    public List<Tuple2<Long, String>> getReceivers(int burningManSelectionHeight,
                                                   long inputAmount,
                                                   long tradeTxFee,
                                                   int burningManAddressListVersion) {
        checkArgument(burningManSelectionHeight >= MIN_SNAPSHOT_HEIGHT, "Selection height must be >= " + MIN_SNAPSHOT_HEIGHT);
        Collection<BurningManCandidate> allBurningManCandidates = burningManService.getActiveBurningManCandidates(burningManSelectionHeight);

        Optional<BurningManAddressList> optionalAddressList = getEnforceableAddressList(burningManAddressListVersion);
        List<BurningManCandidate> burningManCandidates = filterCandidates(allBurningManCandidates, optionalAddressList);
        String legacyBurningManAddress = optionalAddressList
                .map(BurningManAddressList::getLegacyBurningManAddress)
                .orElseGet(() -> burningManService.getLegacyBurningManAddress(burningManSelectionHeight));

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
            return List.of(new Tuple2<>(spendableAmount, legacyBurningManAddress));
        }

        long spendableAmount = getSpendableAmount(burningManCandidates.size(), inputAmount, txFeePerVbyte);
        // We only use outputs >= 1000 sat or at least 2 times the cost for the output (32 bytes).
        // If we remove outputs it will be distributed to the remaining receivers.
        long minOutputAmount = Math.max(DPT_MIN_OUTPUT_AMOUNT, txFeePerVbyte * 32 * 2);
        // Sanity check that max share of a non-legacy BM is 20% over MAX_BURN_SHARE (taking into account potential increase due adjustment)
        long maxOutputAmount = Math.round(spendableAmount * (BurningManService.MAX_BURN_SHARE * 1.2));
        // We accumulate small amounts which gets filtered out and subtract it from 1 to get an adjustment factor
        // used later to be applied to the remaining burningmen share.
        double adjustment = 1 - burningManCandidates.stream()
                .filter(candidate -> candidate.getReceiverAddress().isPresent())
                .mapToDouble(candidate -> {
                    double cappedBurnAmountShare = candidate.getCappedBurnAmountShare();
                    long amount = Math.round(cappedBurnAmountShare * spendableAmount);
                    return amount < minOutputAmount ? cappedBurnAmountShare : 0d;
                })
                .sum();

        // FIXME: The small outputs should be filtered out before adjustment, not afterwards. Otherwise, outputs of
        //  amount just under 1000 sats or 64 * fee-rate could get erroneously included and lead to significant
        //  underpaying of the DPT (by perhaps around 5-10% per erroneously included output).
        List<Tuple2<Long, String>> receivers = burningManCandidates.stream()
                .filter(candidate -> candidate.getReceiverAddress().isPresent())
                .map(candidate -> {
                    double cappedBurnAmountShare = candidate.getCappedBurnAmountShare() / adjustment;
                    return new Tuple2<>(Math.round(cappedBurnAmountShare * spendableAmount),
                            candidate.getReceiverAddress().get());
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
                receivers.add(new Tuple2<>(available, legacyBurningManAddress));
            }
        }
        return receivers;
    }

    public List<Integer> getSupportedBurningManAddressListVersions() {
        return burningManAddressListService.getSupportedVersions();
    }

    public int selectBurningManAddressListVersion(Collection<Integer> peerVersions) {
        return burningManAddressListService.selectHighestCommonVersion(peerVersions);
    }

    public void validateDelayedPayoutTxReceivers(List<Tuple2<Long, String>> receivers,
                                                 int burningManAddressListVersion) {
        if (burningManAddressListVersion <= 0) {
            return;
        }

        Optional<BurningManAddressList> optionalAddressList = getEnforceableAddressList(burningManAddressListVersion);
        if (optionalAddressList.isEmpty()) {
            return;
        }

        BurningManAddressList addressList = optionalAddressList.get();
        Set<String> allowedAddresses = addressList.getAllowedAddresses();
        receivers.forEach(receiver -> checkArgument(allowedAddresses.contains(receiver.second),
                "Delayed payout tx receiver %s is not part of Burning Man address list version %s",
                receiver.second,
                burningManAddressListVersion));
    }

    private Optional<BurningManAddressList> getEnforceableAddressList(int burningManAddressListVersion) {
        if (burningManAddressListVersion <= 0) {
            return Optional.empty();
        }

        BurningManAddressList addressList = burningManAddressListService.getAddressList(burningManAddressListVersion);
        if (!addressList.isForCurrentNetwork()) {
            log.warn("Burning Man address list version {} is for network {}, but current network is {}. " +
                            "Skipping Burning Man address list filtering.",
                    burningManAddressListVersion,
                    addressList.getNetwork(),
                    Config.baseCurrencyNetwork().name());
            return Optional.empty();
        }
        return Optional.of(addressList);
    }

    private List<BurningManCandidate> filterCandidates(Collection<BurningManCandidate> candidates,
                                                       Optional<BurningManAddressList> optionalAddressList) {
        if (optionalAddressList.isEmpty()) {
            return candidates.stream().collect(Collectors.toList());
        }

        BurningManAddressList addressList = optionalAddressList.get();
        Set<String> allowedAddresses = addressList.getAllowedAddresses();
        Map<String, Double> cappedBurnAmountShareByAddress = addressList.getCappedBurnAmountShareByAddress();
        return candidates.stream()
                .filter(candidate -> candidate.getReceiverAddress().isPresent())
                .filter(candidate -> {
                    String receiverAddress = candidate.getReceiverAddress().get();
                    boolean allowed = allowedAddresses.contains(receiverAddress);
                    if (!allowed) {
                        log.warn("Skipping Burning Man receiver {} because it is not in address list version {}",
                                receiverAddress,
                                addressList.getListVersion());
                    }
                    return allowed;
                })
                .filter(candidate -> isCappedBurnAmountShareInRange(candidate, addressList, cappedBurnAmountShareByAddress))
                .collect(Collectors.toList());
    }

    private boolean isCappedBurnAmountShareInRange(BurningManCandidate candidate,
                                                   BurningManAddressList addressList,
                                                   Map<String, Double> cappedBurnAmountShareByAddress) {
        String receiverAddress = candidate.getReceiverAddress().orElseThrow();
        Double referenceShare = cappedBurnAmountShareByAddress.get(receiverAddress);
        if (referenceShare == null && receiverAddress.equals(addressList.getLegacyBurningManAddress())) {
            return true;
        }

        if (referenceShare == null) {
            log.warn("Skipping Burning Man receiver {} because no cappedBurnAmountShare exists in address list version {}",
                    receiverAddress,
                    addressList.getListVersion());
            return false;
        }

        double cappedBurnAmountShare = candidate.getCappedBurnAmountShare();
        double lowerBound = Math.max(0, referenceShare * (1 - BM_ADDRESS_LIST_SHARE_RANGE_TOLERANCE));
        double upperBound = referenceShare * (1 + BM_ADDRESS_LIST_SHARE_RANGE_TOLERANCE);
        boolean inRange = cappedBurnAmountShare >= lowerBound && cappedBurnAmountShare <= upperBound;
        if (!inRange) {
            log.warn("Skipping Burning Man receiver {} because cappedBurnAmountShare {} is outside address list " +
                            "version {} range [{}, {}] with reference share {}",
                    receiverAddress,
                    cappedBurnAmountShare,
                    addressList.getListVersion(),
                    lowerBound,
                    upperBound,
                    referenceShare);
        }
        return inRange;
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
    public static int getSnapshotHeight(int genesisHeight, int height, int grid, int minSnapshotHeight) {
        if (height > (genesisHeight + 3 * grid)) {
            int ratio = (int) Math.round(height / (double) grid);
            return Math.max(minSnapshotHeight, ratio * grid - grid);
        } else {
            return Math.max(minSnapshotHeight, genesisHeight);
        }
    }
}

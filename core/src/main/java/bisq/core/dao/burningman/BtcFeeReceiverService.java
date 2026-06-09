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

import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.filter.FilterPolicyService;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Splitter;
import com.google.common.annotations.VisibleForTesting;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BtcFeeReceiverService implements DaoStateListener {
    @VisibleForTesting
    static final int RECEIVER_SELECTION_CEILING = 10000;

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal RECEIVER_SELECTION_CEILING_AS_DECIMAL = BigDecimal.valueOf(RECEIVER_SELECTION_CEILING);

    private final BurningManService burningManService;
    private final FilterPolicyService filterPolicyService;

    private int currentChainHeight;

    @Inject
    public BtcFeeReceiverService(DaoStateService daoStateService,
                                 BurningManService burningManService,
                                 FilterPolicyService filterPolicyService) {
        this.burningManService = burningManService;
        this.filterPolicyService = filterPolicyService;

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

    public String getAddress() {
        FeeReceiverConfig feeReceiverConfig = getFeeReceiverConfig();

        List<String> receiverAddresses = new ArrayList<>(feeReceiverConfig.getReceiverAddresses());
        List<Long> amountList = new ArrayList<>(feeReceiverConfig.getReceiverWeights());
        addBurningManFeeReceivers(receiverAddresses, amountList, feeReceiverConfig.getBurningManReceiverWeight());

        int winnerIndex = getRandomIndex(amountList, new Random());
        if (winnerIndex == -1) {
            return burningManService.getLegacyBurningManAddress(currentChainHeight);
        }
        return receiverAddresses.get(winnerIndex);
    }

    public static void validateBtcFeeReceiverAddresses(List<String> btcFeeReceiverAddresses) {
        parseBtcFeeReceiverAddresses(btcFeeReceiverAddresses);
    }

    public static List<String> getConfiguredReceiverAddresses(List<String> btcFeeReceiverAddresses) {
        try {
            return parseBtcFeeReceiverAddresses(btcFeeReceiverAddresses).getReceiverAddresses();
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring invalid BTC fee receiver filter configuration: {}", exception.getMessage());
            return List.of();
        }
    }

    private FeeReceiverConfig getFeeReceiverConfig() {
        try {
            return parseBtcFeeReceiverAddresses(filterPolicyService.getBtcFeeReceiverAddresses());
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring invalid BTC fee receiver filter configuration: {}", exception.getMessage());
            return FeeReceiverConfig.forBurningManOnly();
        }
    }

    private void addBurningManFeeReceivers(List<String> receiverAddresses, List<Long> amountList, long ceiling) {
        if (ceiling == 0) {
            return;
        }

        List<BurningManCandidate> activeBurningManCandidates = burningManService.getActiveBurningManCandidates(currentChainHeight);
        if (activeBurningManCandidates.isEmpty()) {
            // If there are no compensation requests (e.g. at dev testing) we fall back to the default address
            receiverAddresses.add(burningManService.getLegacyBurningManAddress(currentChainHeight));
            amountList.add(ceiling);
            return;
        }

        // It might be that we do not reach 100% if some entries had a cappedBurnAmountShare.
        // In that case we fill up the gap to 100% with the legacy BM.
        // cappedBurnAmountShare is a % value represented as double. Smallest supported value is 0.01% -> 0.0001.
        // By multiplying it with 10000 and using Math.floor we limit the candidate to 0.01%.
        // Entries with 0 will be ignored in the selection method, so we do not need to filter them out.
        String legacyBurningManAddress = burningManService.getLegacyBurningManAddress(currentChainHeight);
        List<Long> burningManAmountList = activeBurningManCandidates.stream()
                .map(BurningManCandidate::getCappedBurnAmountShare)
                .map(cappedBurnAmountShare -> (long) Math.floor(cappedBurnAmountShare * ceiling))
                .toList();
        long sum = burningManAmountList.stream().mapToLong(e -> e).sum();

        for (int i = 0; i < activeBurningManCandidates.size(); i++) {
            receiverAddresses.add(activeBurningManCandidates.get(i).getReceiverAddress().orElse(legacyBurningManAddress));
            amountList.add(burningManAmountList.get(i));
        }

        // If we have not reached the 100% we fill the missing gap with the legacy BM
        if (sum < ceiling) {
            receiverAddresses.add(legacyBurningManAddress);
            amountList.add(ceiling - sum);
        }
    }

    @VisibleForTesting
    static FeeReceiverConfig parseBtcFeeReceiverAddresses(List<String> btcFeeReceiverAddresses) {
        List<String> receiverEntries = splitReceiverEntries(btcFeeReceiverAddresses);
        if (receiverEntries.isEmpty()) {
            return FeeReceiverConfig.forBurningManOnly();
        }

        boolean hasWeightedReceivers = receiverEntries.stream().anyMatch(entry -> entry.contains("#"));
        boolean hasPlainReceivers = receiverEntries.stream().anyMatch(entry -> !entry.contains("#"));
        if (hasWeightedReceivers && hasPlainReceivers) {
            throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Do not mix weighted address#fraction entries and plain address entries.");
        }

        if (hasPlainReceivers) {
            return FeeReceiverConfig.forPlainReceivers(receiverEntries);
        }

        return parseWeightedBtcFeeReceiverAddresses(receiverEntries);
    }

    private static List<String> splitReceiverEntries(List<String> btcFeeReceiverAddresses) {
        if (btcFeeReceiverAddresses == null) {
            return List.of();
        }
        return btcFeeReceiverAddresses.stream()
                .flatMap(BtcFeeReceiverService::splitReceiverEntry)
                .toList();
    }

    private static Stream<String> splitReceiverEntry(String entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Entries must not be null.");
        }
        return Splitter.on(';').trimResults().omitEmptyStrings().splitToStream(entry);
    }

    private static FeeReceiverConfig parseWeightedBtcFeeReceiverAddresses(List<String> receiverEntries) {
        List<String> receiverAddresses = new ArrayList<>();
        List<Long> receiverWeights = new ArrayList<>();
        BigDecimal totalShare = BigDecimal.ZERO;
        long totalWeight = 0;

        for (String receiverEntry : receiverEntries) {
            String[] receiverSpec = receiverEntry.split("#", -1);
            if (receiverSpec.length != 2) {
                throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Expected address#fraction entries.");
            }

            String address = receiverSpec[0].trim();
            if (address.isEmpty()) {
                throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Address must not be empty.");
            }

            BigDecimal share = parseShare(receiverSpec[1].trim());
            long weight = share.multiply(RECEIVER_SELECTION_CEILING_AS_DECIMAL)
                    .setScale(0, RoundingMode.FLOOR)
                    .longValueExact();
            if (weight == 0) {
                throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Smallest supported fraction is 0.0001.");
            }

            totalShare = totalShare.add(share);
            if (totalShare.compareTo(ONE) > 0) {
                throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Weighted fractions must not sum to more than 1.0.");
            }

            receiverAddresses.add(address);
            receiverWeights.add(weight);
            totalWeight += weight;
        }

        return new FeeReceiverConfig(receiverAddresses, receiverWeights, RECEIVER_SELECTION_CEILING - totalWeight);
    }

    private static BigDecimal parseShare(String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Fraction must not be empty.");
        }

        BigDecimal share;
        try {
            share = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Fraction must be a decimal value.", exception);
        }

        if (share.compareTo(BigDecimal.ZERO) <= 0 || share.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Fraction must be greater than 0 and not more than 1.0.");
        }
        return share;
    }

    @VisibleForTesting
    static int getRandomIndex(List<Long> weights, Random random) {
        long sum = weights.stream().mapToLong(n -> n).sum();
        if (sum == 0) {
            return -1;
        }
        long target = random.longs(0, sum).findFirst().orElseThrow() + 1;
        return findIndex(weights, target);
    }

    @VisibleForTesting
    static int findIndex(List<Long> weights, long target) {
        int currentRange = 0;
        for (int i = 0; i < weights.size(); i++) {
            currentRange += weights.get(i);
            if (currentRange >= target) {
                return i;
            }
        }
        return 0;
    }

    @VisibleForTesting
    @Getter
    static final class FeeReceiverConfig {
        private final List<String> receiverAddresses;
        private final List<Long> receiverWeights;
        private final long burningManReceiverWeight;

        private FeeReceiverConfig(List<String> receiverAddresses,
                                  List<Long> receiverWeights,
                                  long burningManReceiverWeight) {
            this.receiverAddresses = List.copyOf(receiverAddresses);
            this.receiverWeights = List.copyOf(receiverWeights);
            this.burningManReceiverWeight = burningManReceiverWeight;
        }

        private static FeeReceiverConfig forBurningManOnly() {
            return new FeeReceiverConfig(List.of(), List.of(), RECEIVER_SELECTION_CEILING);
        }

        private static FeeReceiverConfig forPlainReceivers(List<String> receiverEntries) {
            return new FeeReceiverConfig(receiverEntries,
                    receiverEntries.stream().map(entry -> 1L).toList(),
                    0);
        }
    }
}

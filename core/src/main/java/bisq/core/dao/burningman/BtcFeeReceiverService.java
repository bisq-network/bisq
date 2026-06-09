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

import bisq.common.config.Config;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import org.bitcoinj.core.Address;

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

    private volatile int currentChainHeight;

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
        return getAddress(ThreadLocalRandom.current());
    }

    @VisibleForTesting
    String getAddress(RandomGenerator random) {
        int chainHeight = currentChainHeight;
        FeeReceiverConfig feeReceiverConfig = getFeeReceiverConfig();

        List<String> receiverAddresses = new ArrayList<>(feeReceiverConfig.getReceiverAddresses());
        List<Long> receiverWeights = new ArrayList<>(feeReceiverConfig.getReceiverWeights());
        addBurningManFeeReceivers(receiverAddresses,
                receiverWeights,
                feeReceiverConfig.getBurningManReceiverWeight(),
                chainHeight);

        int winnerIndex = getRandomIndex(receiverWeights, random);
        if (winnerIndex == -1) {
            return burningManService.getLegacyBurningManAddress(chainHeight);
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

    private void addBurningManFeeReceivers(List<String> receiverAddresses,
                                           List<Long> receiverWeights,
                                           long ceiling,
                                           int chainHeight) {
        if (ceiling <= 0) {
            return;
        }

        List<BurningManCandidate> activeBurningManCandidates = burningManService.getActiveBurningManCandidates(chainHeight);
        if (activeBurningManCandidates.isEmpty()) {
            // If there are no compensation requests (e.g. at dev testing) we fall back to the default address
            receiverAddresses.add(burningManService.getLegacyBurningManAddress(chainHeight));
            receiverWeights.add(ceiling);
            return;
        }

        // It might be that we do not reach 100% if some entries had a cappedBurnAmountShare.
        // In that case we fill up the gap to 100% with the legacy BM.
        // cappedBurnAmountShare is a % value represented as double. Smallest supported value is 0.01% -> 0.0001.
        // By multiplying it with 10000 and using Math.floor we limit the candidate to 0.01%.
        // Entries with 0 will be ignored in the selection method, so we do not need to filter them out.
        String legacyBurningManAddress = burningManService.getLegacyBurningManAddress(chainHeight);
        long assignedBurningManWeight = 0;

        for (int i = 0; i < activeBurningManCandidates.size(); i++) {
            long calculatedWeight = getSelectionWeight(activeBurningManCandidates.get(i).getCappedBurnAmountShare(), ceiling);
            long remainingBurningManWeight = ceiling - assignedBurningManWeight;
            long receiverWeight = Math.min(calculatedWeight, remainingBurningManWeight);
            receiverAddresses.add(activeBurningManCandidates.get(i).getReceiverAddress().orElse(legacyBurningManAddress));
            receiverWeights.add(receiverWeight);
            assignedBurningManWeight += receiverWeight;
        }

        // If we have not reached the 100% we fill the missing gap with the legacy BM
        if (assignedBurningManWeight < ceiling) {
            receiverAddresses.add(legacyBurningManAddress);
            receiverWeights.add(ceiling - assignedBurningManWeight);
        }
    }

    private static long getSelectionWeight(double share, long ceiling) {
        if (!Double.isFinite(share) || share <= 0) {
            return 0;
        }
        return (long) Math.floor(share * ceiling);
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
            validateAddress(address);

            BigDecimal share = parseShare(receiverSpec[1].trim());
            BigDecimal scaledShare = share.multiply(RECEIVER_SELECTION_CEILING_AS_DECIMAL);
            if (scaledShare.remainder(ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Fractions finer than 0.0001 are not allowed.");
            }
            long weight = scaledShare.longValueExact();
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

    private static void validateAddress(String address) {
        try {
            Address.fromString(Config.baseCurrencyNetworkParameters(), address);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid BTC fee receiver configuration. Address is invalid: " + address, exception);
        }
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
    static int getRandomIndex(List<Long> weights, RandomGenerator random) {
        long sum = getWeightSum(weights);
        if (sum == 0) {
            return -1;
        }
        long target = random.nextLong(sum) + 1;
        return findIndex(weights, target);
    }

    private static long getWeightSum(List<Long> weights) {
        long sum = 0;
        for (long weight : weights) {
            if (weight < 0) {
                throw new IllegalArgumentException("Receiver selection weights must not be negative.");
            }
            try {
                sum = Math.addExact(sum, weight);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Total receiver selection weight is too large.", exception);
            }
        }
        return sum;
    }

    @VisibleForTesting
    static int findIndex(List<Long> weights, long target) {
        long currentRange = 0;
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

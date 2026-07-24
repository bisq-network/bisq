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
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterPolicyService;

import bisq.common.config.Config;

import com.google.common.primitives.Longs;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BtcFeeReceiverServiceTest {
    private static final int LIST_VERSION = 1;
    private static final int SELECTION_HEIGHT = 767950;
    private static final String LEGACY_ADDRESS = "legacyAddress";
    private static final String LIST_LEGACY_ADDRESS = "listLegacyAddress";
    private static final String ALLOWED_ADDRESS = "allowedAddress";
    private static final String UNLISTED_ADDRESS = "unlistedAddress";
    private static final String ADDRESS_1 = "1BoatSLRHtKNngkdXEeobR76b53LETtpyT";
    private static final String ADDRESS_2 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";

    @Test
    public void testGetRandomIndex() {
        Random rnd = new Random(456);
        assertEquals(4, BtcFeeReceiverService.getRandomIndex(Longs.asList(0, 0, 0, 3, 3), rnd));
        assertEquals(3, BtcFeeReceiverService.getRandomIndex(Longs.asList(0, 0, 0, 6, 0, 0, 0, 0, 0), rnd));

        assertEquals(-1, BtcFeeReceiverService.getRandomIndex(Longs.asList(), rnd));
        assertEquals(-1, BtcFeeReceiverService.getRandomIndex(Longs.asList(0), rnd));
        assertEquals(-1, BtcFeeReceiverService.getRandomIndex(Longs.asList(0, 0), rnd));

        int[] selections = new int[3];
        for (int i = 0; i < 6000; i++) {
            selections[BtcFeeReceiverService.getRandomIndex(Longs.asList(1, 2, 3), rnd)]++;
        }
        // selections with new Random(456) are: [986, 1981, 3033]
        assertEquals(1000.0, selections[0], 100);
        assertEquals(2000.0, selections[1], 100);
        assertEquals(3000.0, selections[2], 100);
    }

    @Test
    public void testFindIndex() {
        List<Long> weights = Longs.asList(1, 2, 3);
        assertEquals(0, BtcFeeReceiverService.findIndex(weights, 1));
        assertEquals(1, BtcFeeReceiverService.findIndex(weights, 2));
        assertEquals(1, BtcFeeReceiverService.findIndex(weights, 3));
        assertEquals(2, BtcFeeReceiverService.findIndex(weights, 4));
        assertEquals(2, BtcFeeReceiverService.findIndex(weights, 5));
        assertEquals(2, BtcFeeReceiverService.findIndex(weights, 6));

        // invalid values return index 0
        assertEquals(0, BtcFeeReceiverService.findIndex(weights, 0));
        assertEquals(0, BtcFeeReceiverService.findIndex(weights, 7));

        assertEquals(0, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 2, 3), 0));
        assertEquals(0, BtcFeeReceiverService.findIndex(Longs.asList(1, 2, 3), 0));
        assertEquals(0, BtcFeeReceiverService.findIndex(Longs.asList(1, 2, 3), 1));
        assertEquals(1, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 2, 3), 1));
        assertEquals(2, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 2, 3), 2));
        assertEquals(1, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 0, 2, 3), 1));
        assertEquals(3, BtcFeeReceiverService.findIndex(Longs.asList(0, 1, 0, 2, 3), 2));
        assertEquals(3, BtcFeeReceiverService.findIndex(Longs.asList(0, 0, 0, 1, 2, 3), 1));
        assertEquals(4, BtcFeeReceiverService.findIndex(Longs.asList(0, 0, 0, 1, 2, 3), 2));
        assertEquals(6, BtcFeeReceiverService.findIndex(Longs.asList(0, 0, 0, 1, 0, 0, 2, 3), 2));
    }

    @Test
    public void findIndexUsesLongAccumulator() {
        List<Long> weights = List.of((long) Integer.MAX_VALUE, 1L);

        assertEquals(0, BtcFeeReceiverService.findIndex(weights, Integer.MAX_VALUE));
        assertEquals(1, BtcFeeReceiverService.findIndex(weights, (long) Integer.MAX_VALUE + 1));
    }

    @Test
    public void getRandomIndexRejectsNegativeWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> BtcFeeReceiverService.getRandomIndex(List.of(1L, -1L, 1L), new Random(1)));
    }

    @Test
    public void parseWeightedFilterReceiversKeepsRemainderForBurningMan() {
        BtcFeeReceiverService.FeeReceiverConfig config = BtcFeeReceiverService.parseBtcFeeReceiverAddresses(
                List.of(ADDRESS_1 + "#0.2;" + ADDRESS_2 + "#0.3"));

        assertEquals(List.of(ADDRESS_1, ADDRESS_2), config.getReceiverAddresses());
        assertEquals(List.of(2000L, 3000L), config.getReceiverWeights());
        assertEquals(5000L, config.getBurningManReceiverWeight());
    }

    @Test
    public void parsePlainFilterReceiversUsesLegacyUniformSelection() {
        BtcFeeReceiverService.FeeReceiverConfig config = BtcFeeReceiverService.parseBtcFeeReceiverAddresses(
                List.of("address1", "address2"));

        assertEquals(List.of("address1", "address2"), config.getReceiverAddresses());
        assertEquals(List.of(1L, 1L), config.getReceiverWeights());
        assertEquals(0L, config.getBurningManReceiverWeight());
    }

    @Test
    public void parseEmptyFilterReceiversUsesBurningManOnly() {
        BtcFeeReceiverService.FeeReceiverConfig config = BtcFeeReceiverService.parseBtcFeeReceiverAddresses(List.of());

        assertEquals(List.of(), config.getReceiverAddresses());
        assertEquals(List.of(), config.getReceiverWeights());
        assertEquals(BtcFeeReceiverService.RECEIVER_SELECTION_CEILING, config.getBurningManReceiverWeight());
    }

    @Test
    public void rejectsMixedPlainAndWeightedFilterReceivers() {
        assertThrows(IllegalArgumentException.class,
                () -> BtcFeeReceiverService.parseBtcFeeReceiverAddresses(List.of("address1#0.2", "address2")));
    }

    @Test
    public void rejectsWeightedFilterReceiversOverOneHundredPercent() {
        assertThrows(IllegalArgumentException.class,
                () -> BtcFeeReceiverService.parseBtcFeeReceiverAddresses(List.of(ADDRESS_1 + "#0.8;" + ADDRESS_2 + "#0.3")));
    }

    @Test
    public void rejectsWeightedFilterReceiversWithInvalidAddress() {
        assertThrows(IllegalArgumentException.class,
                () -> BtcFeeReceiverService.parseBtcFeeReceiverAddresses(List.of("not-an-address#0.2")));
    }

    @Test
    public void rejectsWeightedFilterReceiversWithTooFineFraction() {
        assertThrows(IllegalArgumentException.class,
                () -> BtcFeeReceiverService.parseBtcFeeReceiverAddresses(List.of(ADDRESS_1 + "#0.00011")));
    }

    @Test
    public void extractsConfiguredReceiverAddressesForMempoolValidation() {
        assertEquals(List.of(ADDRESS_1, ADDRESS_2),
                BtcFeeReceiverService.getConfiguredReceiverAddresses(List.of(ADDRESS_1 + "#0.2;" + ADDRESS_2 + "#0.3")));
    }

    @Test
    public void weightedBurningManRemainderKeepsSelectionCeiling() {
        BtcFeeReceiverService service = newService(
                List.of(ADDRESS_1 + "#0.5"),
                List.of(candidate("candidate1Address", 0.8),
                        candidate("candidate2Address", 0.8)));
        RecordingRandomGenerator random = new RecordingRandomGenerator(0);

        assertEquals(ADDRESS_1, service.getAddress(random));
        assertEquals(BtcFeeReceiverService.RECEIVER_SELECTION_CEILING, random.getLastBound());
    }

    @Test
    public void getAddressFiltersCandidatesNotInLatestAddressList() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(1))),
                List.of(candidate(ALLOWED_ADDRESS, 1),
                        candidate(UNLISTED_ADDRESS, 1)));

        assertEquals(ALLOWED_ADDRESS, service.getAddress());
    }

    @Test
    public void getAddressUsesAddressListLegacyAddressForRemainder() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(0))),
                List.of(candidate(ALLOWED_ADDRESS, 0)));

        assertEquals(LIST_LEGACY_ADDRESS, service.getAddress());
    }

    @Test
    public void getAddressReturnsAddressListLegacyAddressWhenAllCandidatesAreFiltered() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(1))),
                List.of(candidate(UNLISTED_ADDRESS, 1)));

        assertEquals(LIST_LEGACY_ADDRESS, service.getAddress());
    }

    @Test
    public void getAddressKeepsAllowedCandidateWhenShareDiffersFromAddressList() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(0.05))),
                List.of(candidate(ALLOWED_ADDRESS, 1)));

        assertEquals(ALLOWED_ADDRESS, service.getAddress(new RecordingRandomGenerator(500)));
    }

    @Test
    public void getAddressKeepsAllowedDuplicateCandidatesForSameAddress() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(0.05))),
                List.of(candidate(ALLOWED_ADDRESS, 0.05),
                        candidate(ALLOWED_ADDRESS, 0.05)));

        assertEquals(ALLOWED_ADDRESS, service.getAddress(new RecordingRandomGenerator(500)));
    }

    @Test
    public void getAddressIgnoresNetworkMismatchedAddressList() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList("OTHER_NETWORK", List.of(entry(1))),
                List.of(candidate(UNLISTED_ADDRESS, 1)));

        assertEquals(UNLISTED_ADDRESS, service.getAddress());
    }

    @Test
    public void getAddressRedirectsAttackerShareToAddressListLegacyAddress() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(0.05))),
                List.of(candidate("attacker1", 0.4),
                        candidate("attacker2", 0.4),
                        candidate(ALLOWED_ADDRESS, 0.05)));

        // After filtering, weights are [500, 9500] (allowed candidate + legacy gap fill).
        // Target=101 lands in the allowed range [1, 500].
        assertEquals(ALLOWED_ADDRESS, service.getAddress(new RecordingRandomGenerator(100)));
        // Target=5001 lands in the legacy remainder range [501, 10000].
        assertEquals(LIST_LEGACY_ADDRESS, service.getAddress(new RecordingRandomGenerator(5000)));
    }

    @Test
    public void getAddressFiltersCandidateWithMissingReceiverAddress() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(1))),
                List.of(candidate(null, 1)));

        assertEquals(LIST_LEGACY_ADDRESS, service.getAddress());
    }

    @Test
    public void getAddressAllowsCandidateMatchingAddressListLegacyAddress() {
        BtcFeeReceiverService service = newService(
                List.of(),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(1))),
                List.of(candidate(LIST_LEGACY_ADDRESS, 1)));

        assertEquals(LIST_LEGACY_ADDRESS, service.getAddress());
    }

    @Test
    public void weightedFilterRemainderUsesAddressListLegacyWhenBurningManCandidatesAreFiltered() {
        BtcFeeReceiverService service = newService(
                List.of(ADDRESS_1 + "#0.5"),
                addressList(Config.baseCurrencyNetwork().name(), List.of(entry(1))),
                List.of(candidate(UNLISTED_ADDRESS, 1)));
        RecordingRandomGenerator random = new RecordingRandomGenerator(6000);

        assertEquals(LIST_LEGACY_ADDRESS, service.getAddress(random));
        assertEquals(BtcFeeReceiverService.RECEIVER_SELECTION_CEILING, random.getLastBound());
    }

    private static BtcFeeReceiverService newService(List<String> filterReceivers,
                                                    List<BurningManCandidate> candidates) {
        return newService(filterReceivers,
                addressList(Config.baseCurrencyNetwork().name(), LEGACY_ADDRESS, entriesForCandidates(candidates)),
                candidates);
    }

    private static BtcFeeReceiverService newService(List<String> filterReceivers,
                                                    BurningManAddressList addressList,
                                                    List<BurningManCandidate> candidates) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getLastBlock()).thenReturn(Optional.empty());
        BurningManService burningManService = mock(BurningManService.class);
        when(burningManService.getActiveBurningManCandidates(0)).thenReturn(candidates);
        when(burningManService.getLegacyBurningManAddress(0)).thenReturn(LEGACY_ADDRESS);
        BurningManAddressListService burningManAddressListService = mock(BurningManAddressListService.class);
        when(burningManAddressListService.getLatestVersion()).thenReturn(LIST_VERSION);
        when(burningManAddressListService.getAddressList(LIST_VERSION)).thenReturn(addressList);
        FilterPolicyService filterPolicyService = mock(FilterPolicyService.class);
        when(filterPolicyService.getBtcFeeReceiverAddresses()).thenReturn(filterReceivers);
        return new BtcFeeReceiverService(daoStateService,
                burningManService,
                burningManAddressListService,
                filterPolicyService);
    }

    private static BurningManAddressList addressList(String network,
                                                     List<BurningManAddressList.Entry> entries) {
        return addressList(network, LIST_LEGACY_ADDRESS, entries);
    }

    private static BurningManAddressList addressList(String network,
                                                     String legacyBurningManAddress,
                                                     List<BurningManAddressList.Entry> entries) {
        return new BurningManAddressList(BurningManAddressList.SCHEMA_VERSION,
                LIST_VERSION,
                network,
                SELECTION_HEIGHT,
                SELECTION_HEIGHT,
                legacyBurningManAddress,
                entries);
    }

    private static List<BurningManAddressList.Entry> entriesForCandidates(List<BurningManCandidate> candidates) {
        return candidates.stream()
                .map(BurningManCandidate::getReceiverAddress)
                .flatMap(Optional::stream)
                .distinct()
                .map(receiverAddress -> entry(receiverAddress, 1))
                .toList();
    }

    private static BurningManAddressList.Entry entry(double cappedBurnAmountShare) {
        return entry(ALLOWED_ADDRESS, cappedBurnAmountShare);
    }

    private static BurningManAddressList.Entry entry(String receiverAddress, double cappedBurnAmountShare) {
        return new BurningManAddressList.Entry(receiverAddress, cappedBurnAmountShare);
    }

    private static BurningManCandidate candidate(@Nullable String receiverAddress, double cappedBurnAmountShare) {
        return new TestBurningManCandidate(receiverAddress, cappedBurnAmountShare);
    }

    private static class TestBurningManCandidate extends BurningManCandidate {
        private TestBurningManCandidate(@Nullable String receiverAddress, double cappedBurnAmountShare) {
            this.receiverAddress = Optional.ofNullable(receiverAddress);
            this.cappedBurnAmountShare = cappedBurnAmountShare;
        }
    }

    private static class RecordingRandomGenerator implements RandomGenerator {
        private final long nextLong;
        private long lastBound;

        private RecordingRandomGenerator(long nextLong) {
            this.nextLong = nextLong;
        }

        @Override
        public long nextLong() {
            return nextLong;
        }

        @Override
        public long nextLong(long bound) {
            lastBound = bound;
            return nextLong;
        }

        private long getLastBound() {
            return lastBound;
        }
    }
}

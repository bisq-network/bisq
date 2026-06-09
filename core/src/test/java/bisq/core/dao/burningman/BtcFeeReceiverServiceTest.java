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

import com.google.common.primitives.Longs;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BtcFeeReceiverServiceTest {
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

    private static BtcFeeReceiverService newService(List<String> filterReceivers,
                                                    List<BurningManCandidate> candidates) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getLastBlock()).thenReturn(Optional.empty());
        BurningManService burningManService = mock(BurningManService.class);
        when(burningManService.getActiveBurningManCandidates(0)).thenReturn(candidates);
        when(burningManService.getLegacyBurningManAddress(0)).thenReturn("legacyAddress");
        FilterPolicyService filterPolicyService = mock(FilterPolicyService.class);
        when(filterPolicyService.getBtcFeeReceiverAddresses()).thenReturn(filterReceivers);
        return new BtcFeeReceiverService(daoStateService, burningManService, filterPolicyService);
    }

    private static BurningManCandidate candidate(String receiverAddress, double cappedBurnAmountShare) {
        return new TestBurningManCandidate(receiverAddress, cappedBurnAmountShare);
    }

    private static class TestBurningManCandidate extends BurningManCandidate {
        private TestBurningManCandidate(String receiverAddress, double cappedBurnAmountShare) {
            this.receiverAddress = Optional.of(receiverAddress);
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

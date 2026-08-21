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

import bisq.common.config.Config;
import bisq.common.util.Tuple2;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DelayedPayoutTxReceiverServiceTest {
    private static final int SELECTION_HEIGHT = 767950;
    private static final long INPUT_AMOUNT = 1_000_000L;
    private static final long TRADE_TX_FEE = 2_780L;
    private static final String LEGACY_ADDRESS = "legacy";
    private static final String ALLOWED_ADDRESS_1 = "allowed-1";
    private static final String ALLOWED_ADDRESS_2 = "allowed-2";
    private static final String UNLISTED_ADDRESS = "unlisted";

    @Test
    public void testGetSnapshotHeight() {
        // up to genesis + 3* grid we use genesis
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 0, 10, 0));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 100, 10, 0));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 102, 10, 0));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 119, 10, 0));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 120, 10, 0));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 121, 10, 0));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 130, 10, 0));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 131, 10, 0));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 132, 10, 0));

        assertEquals(120, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 133, 10, 0));
        assertEquals(120, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 134, 10, 0));

        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 135, 10, 0));
        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 136, 10, 0));
        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 139, 10, 0));
        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 140, 10, 0));
        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 141, 10, 0));

        assertEquals(140, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 149, 10, 0));
        assertEquals(140, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 150, 10, 0));
        assertEquals(140, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 151, 10, 0));

        assertEquals(150, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 159, 10, 0));

        assertEquals(990, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 1000, 10, 0));
    }

    @Test
    public void getReceiversFiltersCandidatesNotInSelectedAddressList() {
        DelayedPayoutTxReceiverService service = newService(
                addressList(List.of(entry(ALLOWED_ADDRESS_1, 0.05))),
                List.of(candidate(ALLOWED_ADDRESS_1, 0.05),
                        candidate(UNLISTED_ADDRESS, 0.05)));

        List<Tuple2<Long, String>> receivers = service.getReceivers(SELECTION_HEIGHT, INPUT_AMOUNT, TRADE_TX_FEE, 1);
        List<String> receiverAddresses = getReceiverAddresses(receivers);

        assertTrue(receiverAddresses.contains(ALLOWED_ADDRESS_1));
        assertTrue(receiverAddresses.contains(LEGACY_ADDRESS));
        assertFalse(receiverAddresses.contains(UNLISTED_ADDRESS));
    }

    @Test
    public void getReceiversFiltersCandidatesOutsideReferenceShareRange() {
        DelayedPayoutTxReceiverService service = newService(
                addressList(List.of(entry(ALLOWED_ADDRESS_1, 0.05),
                        entry(ALLOWED_ADDRESS_2, 0.05))),
                List.of(candidate(ALLOWED_ADDRESS_1, 0.05),
                        candidate(ALLOWED_ADDRESS_2, 0.08)));

        List<Tuple2<Long, String>> receivers = service.getReceivers(SELECTION_HEIGHT, INPUT_AMOUNT, TRADE_TX_FEE, 1);
        List<String> receiverAddresses = getReceiverAddresses(receivers);

        assertTrue(receiverAddresses.contains(ALLOWED_ADDRESS_1));
        assertTrue(receiverAddresses.contains(LEGACY_ADDRESS));
        assertFalse(receiverAddresses.contains(ALLOWED_ADDRESS_2));
    }

    @Test
    public void getReceiversUsesFilteredCandidateCountForSpendableAmount() {
        DelayedPayoutTxReceiverService service = newService(
                addressList(List.of(entry(ALLOWED_ADDRESS_1, 0.05))),
                List.of(candidate(ALLOWED_ADDRESS_1, 0.05),
                        candidate(UNLISTED_ADDRESS, 0.05)));

        List<Tuple2<Long, String>> receivers = service.getReceivers(SELECTION_HEIGHT, INPUT_AMOUNT, TRADE_TX_FEE, 1);

        assertEquals(49_950L, receivers.stream()
                .filter(receiver -> ALLOWED_ADDRESS_1.equals(receiver.second))
                .findFirst()
                .orElseThrow()
                .first);
    }

    @Test
    public void validateDelayedPayoutTxReceiversRejectsReceiverOutsideSelectedAddressList() {
        DelayedPayoutTxReceiverService service = newService(
                addressList(List.of(entry(ALLOWED_ADDRESS_1, 0.05))),
                List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.validateDelayedPayoutTxReceivers(List.of(new Tuple2<>(1_000L, UNLISTED_ADDRESS)), 1));
    }

    private static DelayedPayoutTxReceiverService newService(BurningManAddressList addressList,
                                                             List<BurningManCandidate> candidates) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getLastBlock()).thenReturn(Optional.empty());
        BurningManService burningManService = mock(BurningManService.class);
        when(burningManService.getActiveBurningManCandidates(SELECTION_HEIGHT)).thenReturn(candidates);
        when(burningManService.getLegacyBurningManAddress(SELECTION_HEIGHT)).thenReturn(LEGACY_ADDRESS);
        BurningManAddressListService burningManAddressListService = mock(BurningManAddressListService.class);
        when(burningManAddressListService.getAddressList(1)).thenReturn(addressList);
        return new DelayedPayoutTxReceiverService(daoStateService, burningManService, burningManAddressListService);
    }

    private static BurningManAddressList addressList(List<BurningManAddressList.Entry> entries) {
        return new BurningManAddressList(BurningManAddressList.SCHEMA_VERSION,
                1,
                Config.baseCurrencyNetwork().name(),
                SELECTION_HEIGHT,
                SELECTION_HEIGHT,
                LEGACY_ADDRESS,
                entries);
    }

    private static BurningManAddressList.Entry entry(String receiverAddress, double cappedBurnAmountShare) {
        return new BurningManAddressList.Entry(receiverAddress, cappedBurnAmountShare);
    }

    private static BurningManCandidate candidate(String receiverAddress, double cappedBurnAmountShare) {
        return new TestBurningManCandidate(receiverAddress, cappedBurnAmountShare);
    }

    private static List<String> getReceiverAddresses(List<Tuple2<Long, String>> receivers) {
        return receivers.stream()
                .map(receiver -> receiver.second)
                .collect(Collectors.toList());
    }

    private static class TestBurningManCandidate extends BurningManCandidate {
        private TestBurningManCandidate(String receiverAddress, double cappedBurnAmountShare) {
            this.receiverAddress = Optional.of(receiverAddress);
            this.cappedBurnAmountShare = cappedBurnAmountShare;
        }
    }
}

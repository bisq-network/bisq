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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.validation;

import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.ValidationTestUtils.pubKeyRing;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TradeValidationTest {

    /* --------------------------------------------------------------------- */
    // Trade id
    /* --------------------------------------------------------------------- */

    @Test
    void checkTradeIdAcceptsMatchingTradeMessageTradeId() {
        String tradeId = "trade-id";

        assertSame(tradeId, TradeValidation.checkTradeId(tradeId, ValidationTestUtils.tradeMessage(tradeId)));
    }

    @Test
    void checkTradeIdRejectsMismatchingTradeMessageTradeId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkTradeId("trade-id", ValidationTestUtils.tradeMessage("other-trade-id")));

        assertEquals("TradeId trade-id is not valid", exception.getMessage());
    }

    @Test
    void checkTradeIdRejectsNullTradeId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTradeId(null, ValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidReturnsTrueForMatchingTradeMessageTradeId() {
        assertTrue(TradeValidation.isTradeIdValid("trade-id", ValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidReturnsFalseForMismatchingTradeMessageTradeId() {
        assertFalse(TradeValidation.isTradeIdValid("trade-id", ValidationTestUtils.tradeMessage("other-trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.isTradeIdValid(null, ValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeMessage() {
        assertThrows(NullPointerException.class, () -> TradeValidation.isTradeIdValid("trade-id", null));
    }

    /* --------------------------------------------------------------------- */
    // Peer date
    /* --------------------------------------------------------------------- */

    @Test
    void checkPeersDateAcceptsDateWithinAllowedRange() {
        long now = System.currentTimeMillis();

        assertEquals(now, TradeValidation.checkPeersDate(now));
        assertEquals(now - TradeValidation.MAX_DATE_DEVIATION + 60_000,
                TradeValidation.checkPeersDate(now - TradeValidation.MAX_DATE_DEVIATION + 60_000));
        assertEquals(now + TradeValidation.MAX_DATE_DEVIATION - 60_000,
                TradeValidation.checkPeersDate(now + TradeValidation.MAX_DATE_DEVIATION - 60_000));
    }

    @Test
    void checkPeersDateRejectsDateOlderThanAllowedRange() {
        long currentDate = System.currentTimeMillis() - TradeValidation.MAX_DATE_DEVIATION - 60_000;

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(currentDate));
    }

    @Test
    void checkPeersDateRejectsDateNewerThanAllowedRange() {
        long currentDate = System.currentTimeMillis() + TradeValidation.MAX_DATE_DEVIATION + 60_000;

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(currentDate));
    }

    @Test
    void checkPeersDateRejectsZeroDate() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(0));
    }

    /* --------------------------------------------------------------------- */
    // Contract hash
    /* --------------------------------------------------------------------- */

    @Test
    void checkHashFromContractAcceptsMatchingByteArrays() {
        byte[] current = new byte[]{1, 2, 3};

        assertSame(current, TradeValidation.checkHashFromContract(current, new byte[]{1, 2, 3}));
    }

    @Test
    void checkHashFromContractRejectsMismatchingByteArrays() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));

        assertEquals("current is not matching expected. current=010203, expected=010204", exception.getMessage());
    }

    @Test
    void checkHashFromContractRejectsNullAndEmptyByteArrays() {
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkHashFromContract(null, new byte[]{1}));
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1}, null));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[0], new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1}, new byte[0]));
    }

    /* --------------------------------------------------------------------- */
    // Mediator
    /* --------------------------------------------------------------------- */

    @Test
    void getCheckedMediatorPubKeyRingReturnsAcceptedMediatorPubKeyRing() {
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        PubKeyRing mediatorPubKeyRing = pubKeyRing(Sig.generateKeyPair());
        User user = ValidationTestUtils.userWithAcceptedMediator(mediatorNodeAddress,
                ValidationTestUtils.mediator(mediatorNodeAddress, mediatorPubKeyRing));

        assertSame(mediatorPubKeyRing, TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsNullMediatorNodeAddress() {
        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(null, mock(User.class)));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsNullUser() {
        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(new NodeAddress("mediator.onion", 9999), null));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsUnknownMediator() {
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        User user = ValidationTestUtils.userWithAcceptedMediator(mediatorNodeAddress, null);

        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsMediatorWithoutPubKeyRing() {
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        User user = ValidationTestUtils.userWithAcceptedMediator(mediatorNodeAddress, ValidationTestUtils.mediator(mediatorNodeAddress, null));

        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user));
    }

    /* --------------------------------------------------------------------- */
    // Tolerance helper
    /* --------------------------------------------------------------------- */

    @Test
    void checkValueInToleranceRejectsInvalidExpectedValueAndFactor() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkValueInTolerance(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkValueInTolerance(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkValueInTolerance(1, 1, 0.99));
    }
}

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

import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.ValidationTestUtils.pubKeyRing;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        assertEquals("TradeId trade-id is not matching tradeId from message other-trade-id", exception.getMessage());
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
        assertFalse(TradeValidation.isTradeIdValid(null, ValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeMessage() {
        assertFalse(TradeValidation.isTradeIdValid("trade-id", null));
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

    @Test
    void checkPeersDateRejectsExtremeDates() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(Long.MAX_VALUE));
    }

    /* --------------------------------------------------------------------- */
    // Contract hash
    /* --------------------------------------------------------------------- */

    @Test
    void checkHashFromContractAcceptsMatchingByteArrays() {
        byte[] current = new byte[]{1, 2, 3};

        assertSame(current, TradeValidation.checkHashFromContract(current, new byte[]{1, 2, 3}, "testHash"));
    }

    @Test
    void checkHashFromContractRejectsMismatchingByteArrays() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}, "testHash"));

        assertEquals("testHash mismatch. current=010203, expected=010204", exception.getMessage());
    }

    @Test
    void checkHashFromContractRejectsNullAndEmptyByteArrays() {
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkHashFromContract(null, new byte[]{1}, "testHash"));
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1}, null, "testHash"));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[0], new byte[]{1}, "testHash"));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkHashFromContract(new byte[]{1}, new byte[0], "testHash"));
    }

    /* --------------------------------------------------------------------- */
    // Payment method from contract
    /* --------------------------------------------------------------------- */

    @Test
    void checkPaymentMethodFromContractAcceptsPayloadOfPeersPaymentMethod() {
        PubKeyRing myPubKeyRing = pubKeyRing(Sig.generateKeyPair());
        PaymentAccountPayload paymentAccountPayload = paymentAccountPayload("SEPA_INSTANT");

        assertSame(paymentAccountPayload, TradeValidation.checkPaymentMethodFromContract(paymentAccountPayload,
                contractWithPeersPaymentMethodId(myPubKeyRing, "SEPA_INSTANT"),
                myPubKeyRing));
    }

    @Test
    void checkPaymentMethodFromContractRejectsPayloadOfAnotherPaymentMethod() {
        PubKeyRing myPubKeyRing = pubKeyRing(Sig.generateKeyPair());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkPaymentMethodFromContract(paymentAccountPayload("REVOLUT"),
                        contractWithPeersPaymentMethodId(myPubKeyRing, "SEPA"),
                        myPubKeyRing));

        assertEquals("peersPaymentMethodId mismatch. current=REVOLUT, expected=SEPA", exception.getMessage());
    }

    // The payment method of the peer for another key ring is not the one to compare with
    @Test
    void checkPaymentMethodFromContractUsesThePaymentMethodForMyPubKeyRing() {
        PubKeyRing myPubKeyRing = pubKeyRing(Sig.generateKeyPair());
        PubKeyRing peersPubKeyRing = pubKeyRing(Sig.generateKeyPair());
        Contract contract = contractWithPeersPaymentMethodId(myPubKeyRing, "SEPA_INSTANT");
        when(contract.getPeersPaymentMethodId(peersPubKeyRing)).thenReturn("SEPA");

        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkPaymentMethodFromContract(paymentAccountPayload("SEPA"),
                        contract,
                        myPubKeyRing));
    }

    @Test
    void checkPaymentMethodFromContractRejectsMissingPaymentMethodIds() {
        PubKeyRing myPubKeyRing = pubKeyRing(Sig.generateKeyPair());
        Contract contract = contractWithPeersPaymentMethodId(myPubKeyRing, "SEPA");

        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkPaymentMethodFromContract(null, contract, myPubKeyRing));
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkPaymentMethodFromContract(paymentAccountPayload("SEPA"),
                        null,
                        myPubKeyRing));
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkPaymentMethodFromContract(paymentAccountPayload(null),
                        contract,
                        myPubKeyRing));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkPaymentMethodFromContract(paymentAccountPayload(" "),
                        contract,
                        myPubKeyRing));
        // A contract which gives no payment method of the peer must not pass a payload without one either
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkPaymentMethodFromContract(paymentAccountPayload("SEPA"),
                        mock(Contract.class),
                        myPubKeyRing));
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkPaymentMethodFromContract(paymentAccountPayload(null),
                        mock(Contract.class),
                        myPubKeyRing));
    }

    private static PaymentAccountPayload paymentAccountPayload(String paymentMethodId) {
        PaymentAccountPayload paymentAccountPayload = mock(PaymentAccountPayload.class);
        when(paymentAccountPayload.getPaymentMethodId()).thenReturn(paymentMethodId);
        return paymentAccountPayload;
    }

    private static Contract contractWithPeersPaymentMethodId(PubKeyRing myPubKeyRing, String peersPaymentMethodId) {
        Contract contract = mock(Contract.class);
        when(contract.getPeersPaymentMethodId(myPubKeyRing)).thenReturn(peersPaymentMethodId);
        return contract;
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

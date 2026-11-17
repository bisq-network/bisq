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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.offer.Offer;
import bisq.core.payment.payload.CustomPaymentMethodAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The task which takes the account of the buyer when the buyer reveals it. In the normal flow it is the only place
// where the seller checks that the account is for the custom payment method of the offer: the account is marked
// as validated right after it, and a validated account is not checked again.
class SellerProcessShareBuyerCustomPaymentMethodAccountTest {
    private static final NodeAddress NODE_ADDRESS = new NodeAddress("peer.onion", 9999);

    // All custom payment methods share one payment method ID. A buyer with a modified client could commit to an
    // account of another custom payment method than the one of the offer.
    @Test
    void runRejectsBuyerAccountOfAnotherCustomPaymentMethod() {
        Trade trade = trade(customPayload("PayPal"), customOffer("M-Pesa"));

        new SellerProcessShareBuyerPaymentAccountMessage(taskRunner(), trade).run();

        verifyAccountNotApplied(trade);
        verify(trade).setErrorMessage(contains("is not for the custom payment method of the offer"));
    }

    // An account of the custom payment method has no signing rule, so it must not stand in for the account of a
    // payment method which has one
    @Test
    void runRejectsBuyerAccountOfTheCustomPaymentMethodForAnotherPaymentMethod() {
        Offer sepaOffer = mock(Offer.class);
        when(sepaOffer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(PaymentMethod.SEPA_ID));
        Trade trade = trade(customPayload("SEPA"), sepaOffer);

        new SellerProcessShareBuyerPaymentAccountMessage(taskRunner(), trade).run();

        verifyAccountNotApplied(trade);
        verify(trade).setErrorMessage(contains("must both be of the custom payment method"));
    }

    // Shows that the tests above fail for the account and not for another part of their setup: with the name of
    // the offer the same account is taken and the task ends without an error
    @Test
    void runTakesBuyerAccountOfTheCustomPaymentMethodOfTheOffer() {
        CustomPaymentMethodAccountPayload buyerPaymentAccountPayload = customPayload("m pesa");
        Trade trade = trade(buyerPaymentAccountPayload, customOffer("M-Pesa"));
        KeyRing keyRing = mock(KeyRing.class);
        when(keyRing.getSignatureKeyPair()).thenReturn(Sig.generateKeyPair());
        when(trade.getProcessModel().getKeyRing()).thenReturn(keyRing);

        new SellerProcessShareBuyerPaymentAccountMessage(taskRunner(), trade).run();

        verify(trade, never()).setErrorMessage(any());
        verify(trade.getProcessModel().getTradePeer()).setPaymentAccountPayload(buyerPaymentAccountPayload);
        verify(trade).setContractAsJson(any());
        verify(trade).setContractHash(any());
    }

    private static void verifyAccountNotApplied(Trade trade) {
        verify(trade.getProcessModel().getTradePeer(), never()).setPaymentAccountPayload(any());
        verify(trade.getContract(), never()).setPaymentAccountPayloads(any(), any(), any());
        verify(trade, never()).setContractAsJson(any());
        verify(trade, never()).setContractHash(any());
    }

    private static Trade trade(CustomPaymentMethodAccountPayload buyerPaymentAccountPayload, Offer offer) {
        Trade trade = mock(Trade.class);
        ProcessModel processModel = mock(ProcessModel.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getId()).thenReturn("trade-id");
        when(trade.getOffer()).thenReturn(offer);
        when(processModel.getTradeMessage()).thenReturn(new ShareBuyerPaymentAccountMessage("uid",
                "trade-id",
                NODE_ADDRESS,
                buyerPaymentAccountPayload));
        when(processModel.getPaymentAccountPayload(trade)).thenReturn(mock(PaymentAccountPayload.class));
        when(processModel.getTradePeer()).thenReturn(mock(TradingPeer.class));
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));

        Contract contract = mock(Contract.class);
        when(trade.getContract()).thenReturn(contract);
        when(contract.getHashOfPeersPaymentAccountPayload(processModel.getPubKeyRing()))
                .thenReturn(buyerPaymentAccountPayload.getHashForContract());
        return trade;
    }

    private static CustomPaymentMethodAccountPayload customPayload(String paymentMethodName) {
        CustomPaymentMethodAccountPayload payload = new CustomPaymentMethodAccountPayload(PaymentMethod.CUSTOM_ID, "id");
        payload.setPaymentMethodName(paymentMethodName);
        payload.setAccountData("Jane Doe, +254 700 000000");
        return payload;
    }

    // Other tests replace static fields of PaymentMethod with mocks, so the payment method is taken from the list
    private static Offer customOffer(String customPaymentMethodName) {
        Offer offer = mock(Offer.class);
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(PaymentMethod.CUSTOM_ID));
        when(offer.getCustomPaymentMethodName()).thenReturn(customPaymentMethodName);
        return offer;
    }

    @SuppressWarnings("unchecked")
    private static TaskRunner<Trade> taskRunner() {
        return mock(TaskRunner.class);
    }
}

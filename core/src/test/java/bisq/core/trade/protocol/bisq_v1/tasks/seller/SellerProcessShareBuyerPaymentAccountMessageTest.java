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
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.SepaInstantTakerContractFixture;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerProcessShareBuyerPaymentAccountMessageTest {
    private static final NodeAddress NODE_ADDRESS = new NodeAddress("peer.onion", 9999);
    private static final byte[] HASH_IN_CONTRACT = {3};

    // The seller is the maker of a SEPA offer, the buyer has taken it with a SEPA Instant account. The payment
    // method of the buyer is then not the one of the offer and not the one of the seller.
    @Test
    void runAcceptsSepaInstantAccountOfTheTakerOfASepaOffer() {
        SepaInstantTakerContractFixture fixture = new SepaInstantTakerContractFixture(false);
        Trade trade = trade(fixture.takerPaymentAccountPayload, null);
        ProcessModel processModel = trade.getProcessModel();
        when(trade.getContract()).thenReturn(fixture.contract);
        when(processModel.getPubKeyRing()).thenReturn(fixture.makerPubKeyRing);
        when(processModel.getPaymentAccountPayload(trade)).thenReturn(fixture.makerPaymentAccountPayload);
        KeyRing keyRing = mock(KeyRing.class);
        when(keyRing.getSignatureKeyPair()).thenReturn(Sig.generateKeyPair());
        when(processModel.getKeyRing()).thenReturn(keyRing);

        new SellerProcessShareBuyerPaymentAccountMessage(taskRunner(), trade).run();

        verify(trade, never()).setErrorMessage(any());
        verify(processModel.getTradePeer()).setPaymentAccountPayload(fixture.takerPaymentAccountPayload);
        assertSame(fixture.takerPaymentAccountPayload, fixture.contract.getBuyerPaymentAccountPayload());
        assertSame(fixture.makerPaymentAccountPayload, fixture.contract.getSellerPaymentAccountPayload());
        verify(trade).setContractAsJson(any());
        verify(trade).setContractHash(any());
    }

    @Test
    void runRejectsBuyerPaymentAccountWithAnotherHashThanInContract() {
        PaymentAccountPayload buyerPaymentAccountPayload = paymentAccountPayload(new byte[]{4}, "SEPA");
        Trade trade = trade(buyerPaymentAccountPayload, "SEPA");

        new SellerProcessShareBuyerPaymentAccountMessage(taskRunner(), trade).run();

        verifyAccountNotApplied(trade);
        verify(trade).setErrorMessage(contains("peersPaymentAccountPayloadHash mismatch"));
    }

    // The buyer has committed the hash of the revealed account in the contract, but has agreed there to another
    // payment method than the one of that account
    @Test
    void runRejectsBuyerPaymentAccountOfAnotherPaymentMethodThanInContract() {
        PaymentAccountPayload buyerPaymentAccountPayload = paymentAccountPayload(HASH_IN_CONTRACT, "REVOLUT");
        Trade trade = trade(buyerPaymentAccountPayload, "SEPA");

        new SellerProcessShareBuyerPaymentAccountMessage(taskRunner(), trade).run();

        verifyAccountNotApplied(trade);
        verify(trade).setErrorMessage(contains("peersPaymentMethodId mismatch. current=REVOLUT, expected=SEPA"));
    }

    private static void verifyAccountNotApplied(Trade trade) {
        verify(trade.getProcessModel().getTradePeer(), never()).setPaymentAccountPayload(any());
        verify(trade.getContract(), never()).setPaymentAccountPayloads(any(), any(), any());
        verify(trade, never()).setContractAsJson(any());
        verify(trade, never()).setContractHash(any());
    }

    private static Trade trade(PaymentAccountPayload buyerPaymentAccountPayload, String buyersPaymentMethodIdInContract) {
        Trade trade = mock(Trade.class);
        ProcessModel processModel = mock(ProcessModel.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getId()).thenReturn("trade-id");
        when(processModel.getTradeMessage()).thenReturn(new ShareBuyerPaymentAccountMessage("uid",
                "trade-id",
                NODE_ADDRESS,
                buyerPaymentAccountPayload));
        when(processModel.getPaymentAccountPayload(trade)).thenReturn(mock(PaymentAccountPayload.class));
        when(processModel.getTradePeer()).thenReturn(mock(TradingPeer.class));
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        // An offer of a payment method which is not the custom one, as in the fixture
        Offer offer = mock(Offer.class);
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(PaymentMethod.SEPA_ID));
        when(trade.getOffer()).thenReturn(offer);

        Contract contract = mock(Contract.class);
        when(trade.getContract()).thenReturn(contract);
        when(contract.getHashOfPeersPaymentAccountPayload(processModel.getPubKeyRing())).thenReturn(HASH_IN_CONTRACT);
        when(contract.getPeersPaymentMethodId(processModel.getPubKeyRing()))
                .thenReturn(buyersPaymentMethodIdInContract);
        return trade;
    }

    private static PaymentAccountPayload paymentAccountPayload(byte[] hashForContract, String paymentMethodId) {
        PaymentAccountPayload paymentAccountPayload = mock(PaymentAccountPayload.class);
        when(paymentAccountPayload.getHashForContract()).thenReturn(hashForContract);
        when(paymentAccountPayload.getPaymentMethodId()).thenReturn(paymentMethodId);
        return paymentAccountPayload;
    }

    @SuppressWarnings("unchecked")
    private static TaskRunner<Trade> taskRunner() {
        return mock(TaskRunner.class);
    }
}

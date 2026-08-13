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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerVerifyBuyerPaymentAccount;

import bisq.network.p2p.NodeAddress;

import org.bitcoinj.core.Transaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SellerProtocolMessageHandlingTest {
    private static final String TRADE_ID = "trade-id";

    @Test
    void buyerPaymentAccountRevealIsDispatchedFromMailbox() {
        TestSetup setup = new TestSetup();
        ShareBuyerPaymentAccountMessage message = mock(ShareBuyerPaymentAccountMessage.class);
        NodeAddress peer = mock(NodeAddress.class);
        when(message.getTradeId()).thenReturn(TRADE_ID);

        setup.protocol.onMailboxMessage(message, peer);

        assertSame(message, setup.protocol.revealMessage);
        assertSame(peer, setup.protocol.revealPeer);
    }

    @Test
    void duplicatePaymentStartedMessageIsAcknowledgedAfterPayoutCreation() {
        TestSetup setup = new TestSetup();
        CounterCurrencyTransferStartedMessage message = mock(CounterCurrencyTransferStartedMessage.class);
        when(message.getTradeId()).thenReturn(TRADE_ID);
        when(setup.trade.getTradePhase()).thenReturn(Trade.Phase.DEPOSIT_PUBLISHED);
        when(setup.trade.getPayoutTx()).thenReturn(mock(Transaction.class));

        setup.protocol.handle(message, null);

        assertTrue(setup.protocol.ackSent);
        assertTrue(setup.protocol.ackResult);
        assertTrue(setup.protocol.mailboxMessageRemoved);
    }

    private static final class TestSetup {
        private final ProcessModel processModel = mock(ProcessModel.class);
        private final SellerAsMakerTrade trade = mock(SellerAsMakerTrade.class);
        private final TestSellerProtocol protocol;

        private TestSetup() {
            when(trade.getProcessModel()).thenReturn(processModel);
            when(trade.getTradeProtocolModel()).thenReturn(processModel);
            when(trade.getId()).thenReturn(TRADE_ID);
            when(processModel.getOfferId()).thenReturn(TRADE_ID);
            protocol = new TestSellerProtocol(trade);
        }
    }

    private static final class TestSellerProtocol extends SellerProtocol {
        private ShareBuyerPaymentAccountMessage revealMessage;
        private NodeAddress revealPeer;
        private boolean ackSent;
        private boolean ackResult;
        private boolean mailboxMessageRemoved;

        private TestSellerProtocol(SellerAsMakerTrade trade) {
            super(trade);
        }

        @Override
        protected void handle(ShareBuyerPaymentAccountMessage message, NodeAddress peer) {
            revealMessage = message;
            revealPeer = peer;
        }

        @Override
        protected void sendAckMessage(TradeMessage message, boolean result, String errorMessage) {
            ackSent = true;
            ackResult = result;
        }

        @Override
        public void removeMailboxMessageAfterProcessing(TradeMessage tradeMessage) {
            mailboxMessageRemoved = true;
        }

        @Override
        protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass() {
            return SellerVerifyBuyerPaymentAccount.class;
        }
    }
}

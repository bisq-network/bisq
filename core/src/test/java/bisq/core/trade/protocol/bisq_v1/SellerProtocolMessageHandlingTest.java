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

import bisq.core.offer.Offer;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerProcessShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesTradeStatistics;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSendsDepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerVerifyBuyerPaymentAccount;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.taskrunner.Task;

import org.bitcoinj.core.Transaction;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void cryptoBuyerAccountRevealDoesNotScheduleAnotherDepositPublication() {
        TestSetup setup = new TestSetup();
        Offer offer = mock(Offer.class);
        when(offer.isFiatOffer()).thenReturn(false);
        when(setup.trade.getOffer()).thenReturn(offer);
        when(setup.processModel.getDepositTx()).thenReturn(mock(Transaction.class));
        setup.protocol.recordTaskSelection = true;

        setup.protocol.handleBuyerPaymentAccountReveal(mock(ShareBuyerPaymentAccountMessage.class), mock(NodeAddress.class));

        assertArrayEquals(new Class[]{SellerProcessShareBuyerPaymentAccountMessage.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class},
                setup.protocol.selectedTasks);
    }

    @Test
    void fiatBuyerAccountRevealSchedulesPendingDepositPublication() {
        TestSetup setup = new TestSetup();
        Offer offer = mock(Offer.class);
        when(offer.isFiatOffer()).thenReturn(true);
        when(setup.trade.getOffer()).thenReturn(offer);
        when(setup.processModel.getDepositTx()).thenReturn(mock(Transaction.class));
        setup.protocol.recordTaskSelection = true;

        setup.protocol.handleBuyerPaymentAccountReveal(mock(ShareBuyerPaymentAccountMessage.class), mock(NodeAddress.class));

        assertArrayEquals(new Class[]{SellerProcessShareBuyerPaymentAccountMessage.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class,
                        SellerPublishesDepositTx.class,
                        SellerPublishesTradeStatistics.class},
                setup.protocol.selectedTasks);
    }

    // Until the buyer's ACK of the DepositTxAndDelayedPayoutTxMessage, a crypto deposit is not published either
    @Test
    void startupResendsAnUnacknowledgedCryptoDepositMessageBeforePublication() {
        TestSetup setup = new TestSetup();
        setup.startupInTakerFeePublished(false, false);

        setup.protocol.onInitialized();

        assertEquals(List.of(SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                SellerPublishesDepositTx.class,
                SellerPublishesTradeStatistics.class), setup.protocol.startupTasks);
    }

    @Test
    void startupPublishesTheDepositOfAnAcknowledgedCryptoDepositMessage() {
        TestSetup setup = new TestSetup();
        setup.startupInTakerFeePublished(false, true);

        setup.protocol.onInitialized();

        assertEquals(List.of(SellerPublishesDepositTx.class,
                SellerPublishesTradeStatistics.class), setup.protocol.startupTasks);
    }

    // Older versions stored the finalized deposit tx only for fiat trades
    @Test
    void startupResumesNothingWithoutAStoredDepositTx() {
        TestSetup setup = new TestSetup();
        setup.startupInTakerFeePublished(false, false);
        when(setup.processModel.getFinalizedDepositTx()).thenReturn(null);

        setup.protocol.onInitialized();

        assertEquals(List.of(), setup.protocol.startupTasks);
    }

    // The task that sends the deposit message gave up, and the buyer acknowledges the message later
    @Test
    void aLateAckOfTheDepositMessageResumesTheTradeAsARestartDoes() {
        TestSetup setup = new TestSetup();
        setup.startupInTakerFeePublished(false, false);

        setup.protocol.onAckMessage(ack(DepositTxAndDelayedPayoutTxMessage.class, true), null);

        assertEquals(List.of(SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                SellerPublishesDepositTx.class,
                SellerPublishesTradeStatistics.class), setup.protocol.startupTasks);
    }

    // The running task marked the message delivered on this ACK and continues itself
    @Test
    void anAckThatTheSendingTaskAppliedResumesNothing() {
        TestSetup setup = new TestSetup();
        setup.startupInTakerFeePublished(false, true);

        setup.protocol.onAckMessage(ack(DepositTxAndDelayedPayoutTxMessage.class, true), null);

        assertEquals(List.of(), setup.protocol.startupTasks);
    }

    @Test
    void aNackOrTheAckOfAnotherMessageResumesNothing() {
        TestSetup setup = new TestSetup();
        setup.startupInTakerFeePublished(false, false);

        setup.protocol.onAckMessage(ack(DepositTxAndDelayedPayoutTxMessage.class, false), null);
        setup.protocol.onAckMessage(ack(CounterCurrencyTransferStartedMessage.class, true), null);

        assertEquals(List.of(), setup.protocol.startupTasks);
    }

    @Test
    void aLateAckAfterThePublicationResumesNothing() {
        TestSetup setup = new TestSetup();
        setup.startupInTakerFeePublished(false, false);
        when(setup.trade.getTradePhase()).thenReturn(Trade.Phase.DEPOSIT_PUBLISHED);

        setup.protocol.onAckMessage(ack(DepositTxAndDelayedPayoutTxMessage.class, true), null);

        assertEquals(List.of(), setup.protocol.startupTasks);
    }

    private static AckMessage ack(Class<?> sourceMessageClass, boolean success) {
        AckMessage ackMessage = mock(AckMessage.class);
        when(ackMessage.getSourceMsgClassName()).thenReturn(sourceMessageClass.getSimpleName());
        when(ackMessage.isSuccess()).thenReturn(success);
        return ackMessage;
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

        // A trade with the finalized deposit tx and delayed payout tx, whose deposit tx is not published yet
        private void startupInTakerFeePublished(boolean isFiatOffer, boolean isDepositMessageAcknowledged) {
            P2PService p2PService = mock(P2PService.class);
            when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
            when(processModel.getP2PService()).thenReturn(p2PService);
            when(processModel.getFinalizedDepositTx()).thenReturn(new byte[]{1});
            when(processModel.isDepositTxAndDelayedPayoutTxMessageDelivered()).thenReturn(isDepositMessageAcknowledged);
            Offer offer = mock(Offer.class);
            when(offer.isFiatOffer()).thenReturn(isFiatOffer);
            when(trade.getOffer()).thenReturn(offer);
            when(trade.getTradePhase()).thenReturn(Trade.Phase.TAKER_FEE_PUBLISHED);
            when(trade.getDelayedPayoutTxBytes()).thenReturn(new byte[]{1});
            protocol.recordStartupTasks = true;
        }
    }

    private static final class TestSellerProtocol extends SellerProtocol {
        private ShareBuyerPaymentAccountMessage revealMessage;
        private NodeAddress revealPeer;
        private boolean ackSent;
        private boolean ackResult;
        private boolean mailboxMessageRemoved;
        private boolean recordTaskSelection;
        private Class<? extends Task<TradeModel>>[] selectedTasks;
        private boolean recordStartupTasks;
        private final List<Class<? extends Task<TradeModel>>> startupTasks = new ArrayList<>();

        private TestSellerProtocol(SellerAsMakerTrade trade) {
            super(trade);
        }

        @Override
        protected void handle(ShareBuyerPaymentAccountMessage message, NodeAddress peer) {
            revealMessage = message;
            revealPeer = peer;
        }

        private void handleBuyerPaymentAccountReveal(ShareBuyerPaymentAccountMessage message, NodeAddress peer) {
            super.handle(message, peer);
        }

        @Override
        protected FluentProtocol expect(FluentProtocol.Condition condition) {
            if (!recordTaskSelection) {
                return super.expect(condition);
            }
            return new FluentProtocol(this) {
                @Override
                public FluentProtocol setup(Setup setup) {
                    selectedTasks = setup.getTasks();
                    return this;
                }

                @Override
                public FluentProtocol run(Runnable runnable) {
                    return this;
                }

                @Override
                public FluentProtocol executeTasks() {
                    return this;
                }
            };
        }

        // Records the tasks of a startup condition that holds, instead of running them
        @Override
        protected FluentProtocol given(FluentProtocol.Condition condition) {
            if (!recordStartupTasks) {
                return super.given(condition);
            }
            return new FluentProtocol(this) {
                private Class<? extends Task<TradeModel>>[] tasks;

                {
                    condition(condition);
                }

                @Override
                public FluentProtocol setup(Setup setup) {
                    tasks = setup.getTasks();
                    return this;
                }

                @Override
                public FluentProtocol executeTasks() {
                    return run(() -> startupTasks.addAll(List.of(tasks)));
                }
            };
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

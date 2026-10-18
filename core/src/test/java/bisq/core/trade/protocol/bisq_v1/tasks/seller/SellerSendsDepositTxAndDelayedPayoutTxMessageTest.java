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

import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.network.MessageState;
import bisq.core.offer.Offer;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.UserThread;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;

import javafx.beans.property.SimpleObjectProperty;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The seller's task that sends the DepositTxAndDelayedPayoutTxMessage, followed by the publication of the deposit tx,
 * in a real task runner. Only the buyer's ACK lets the deposit tx be published; the arrival of the message at the
 * buyer's node does not show that the buyer processed and kept it.
 */
class SellerSendsDepositTxAndDelayedPayoutTxMessageTest {
    private static final Transaction DEPOSIT_TX = new Transaction(MainNetParams.get());

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    @Test
    void arrivalWithoutAckDoesNotPublishTheDepositTx() {
        Fixture f = new Fixture(false);
        f.run();

        f.listeners.get(0).onArrived();

        assertFalse(f.delivered.get());
        assertFalse(f.completed.get());
        verify(f.tradeWalletService, never()).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
        assertFalse(ManualTimer.latest().isStopped(), "the message is sent again if the ACK does not arrive");
    }

    @Test
    void ackAfterArrivalPublishesTheDepositTx() {
        Fixture f = new Fixture(false);
        f.run();
        f.listeners.get(0).onArrived();

        f.messageState.set(MessageState.ACKNOWLEDGED);

        assertTrue(f.delivered.get());
        verify(f.tradeWalletService).broadcastTx(same(DEPOSIT_TX), any(TxBroadcaster.Callback.class));
        assertTrue(ManualTimer.latest().isStopped());
    }

    // The buyer sends the ACK only after processing the message and sending its payment account to the seller
    @Test
    void afterArrivalTheMessageIsSentAgainOnlyAfterTheAckHadTime() {
        Fixture f = new Fixture(false);
        f.run();

        f.listeners.get(0).onArrived();

        assertTrue(ManualTimer.latest().getDelay().compareTo(Duration.ofSeconds(30)) >= 0,
                "" + ManualTimer.latest().getDelay());
    }

    @Test
    void ackAfterTheMessageWasSentAgainPublishesTheDepositTx() {
        Fixture f = new Fixture(false);
        f.run();
        f.listeners.get(0).onStoredInMailbox();
        ManualTimer.latest().fire();
        assertEquals(2, f.listeners.size());
        f.listeners.get(1).onStoredInMailbox();

        f.messageState.set(MessageState.ACKNOWLEDGED);

        assertTrue(f.delivered.get());
        verify(f.tradeWalletService).broadcastTx(same(DEPOSIT_TX), any(TxBroadcaster.Callback.class));
    }

    // The ACK can have been applied before the task starts, for example from the mailbox
    @Test
    void anAckAppliedBeforeTheTaskStartsPublishesTheDepositTxWithoutSendingAgain() {
        Fixture f = new Fixture(false);
        f.messageState.set(MessageState.ACKNOWLEDGED);

        f.run();

        assertTrue(f.listeners.isEmpty());
        assertTrue(f.delivered.get());
        verify(f.tradeWalletService).broadcastTx(same(DEPOSIT_TX), any(TxBroadcaster.Callback.class));
    }

    @Test
    void ackAndALaterArrivalPublishTheDepositTxOnce() {
        Fixture f = new Fixture(false);
        f.run();
        f.messageState.set(MessageState.ACKNOWLEDGED);

        f.listeners.get(0).onArrived();
        f.messageState.set(MessageState.UNDEFINED);
        f.messageState.set(MessageState.ACKNOWLEDGED);

        verify(f.tradeWalletService, times(1)).broadcastTx(same(DEPOSIT_TX), any(TxBroadcaster.Callback.class));
        verify(f.processModel, times(1)).setDepositTxAndDelayedPayoutTxMessageDelivered(true);
    }

    @Test
    void nackNeitherPublishesNorFails() {
        Fixture f = new Fixture(false);
        f.run();
        f.listeners.get(0).onArrived();

        f.messageState.set(MessageState.FAILED);

        assertFalse(f.completed.get());
        assertNull(f.errorMessage.get());
        verify(f.tradeWalletService, never()).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
    }

    @Test
    void withoutAckAfterAllAttemptsTheTaskFailsWithoutPublication() {
        Fixture f = new Fixture(false);
        f.run();
        for (int attempt = 0; attempt < 7; attempt++) {
            f.listeners.get(attempt).onArrived();
            assertNull(f.errorMessage.get(), "attempt " + attempt);
            ManualTimer.latest().fire();
        }
        f.listeners.get(7).onArrived();

        assertNotNull(f.errorMessage.get());
        assertFalse(f.completed.get());
        // The failed task no longer listens: a late ACK is not applied
        f.messageState.set(MessageState.ACKNOWLEDGED);
        assertFalse(f.delivered.get());
        verify(f.tradeWalletService, never()).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
    }

    @Test
    void fiatDepositTxWaitsForTheBuyerAccountValidationAfterTheAck() {
        Fixture f = new Fixture(true);
        f.run();
        f.listeners.get(0).onArrived();

        f.messageState.set(MessageState.ACKNOWLEDGED);

        assertTrue(f.delivered.get());
        assertTrue(f.completed.get());
        verify(f.tradeWalletService, never()).broadcastTx(any(Transaction.class), any(TxBroadcaster.Callback.class));
    }

    // The publication of a crypto deposit tx completes in the broadcast callback, which the mocked wallet never calls,
    // so for crypto the tests check the broadcast and not the end of the runner
    private static final class Fixture {
        final ProcessModel processModel = mock(ProcessModel.class);
        final Trade trade = mock(Trade.class);
        final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        final SimpleObjectProperty<MessageState> messageState = new SimpleObjectProperty<>(MessageState.UNDEFINED);
        final AtomicBoolean delivered = new AtomicBoolean();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<String> errorMessage = new AtomicReference<>();
        final List<SendMailboxMessageListener> listeners = new ArrayList<>();

        Fixture(boolean fiat) {
            Offer offer = mock(Offer.class);
            when(offer.isFiatOffer()).thenReturn(fiat);
            when(offer.getId()).thenReturn("trade-id");
            when(trade.getOffer()).thenReturn(offer);
            when(trade.getProcessModel()).thenReturn(processModel);
            when(trade.getDelayedPayoutTx()).thenReturn(new Transaction(MainNetParams.get()));
            when(trade.getTradingPeerNodeAddress()).thenReturn(new NodeAddress("buyer.onion", 9999));
            when(processModel.getOffer()).thenReturn(offer);
            when(processModel.getOfferId()).thenReturn("trade-id");
            when(processModel.getMyNodeAddress()).thenReturn(new NodeAddress("seller.onion", 9999));
            when(processModel.getDepositTx()).thenReturn(DEPOSIT_TX);
            when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
            when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
            when(processModel.tryStartDepositTxBroadcast()).thenReturn(true);
            when(processModel.getDepositTxMessageStateProperty()).thenReturn(messageState);
            when(processModel.isDepositTxAndDelayedPayoutTxMessageDelivered()).thenAnswer(i -> delivered.get());
            doAnswer(i -> {
                delivered.set(i.getArgument(0));
                return null;
            }).when(processModel).setDepositTxAndDelayedPayoutTxMessageDelivered(anyBoolean());
            TradingPeer tradingPeer = new TradingPeer();
            tradingPeer.setPubKeyRing(new PubKeyRing(Sig.generateKeyPair().getPublic(),
                    Encryption.generateKeyPair().getPublic()));
            when(processModel.getTradePeer()).thenReturn(tradingPeer);
            P2PService p2PService = mock(P2PService.class);
            MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
            when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
            when(processModel.getP2PService()).thenReturn(p2PService);
            doAnswer(i -> {
                listeners.add(i.getArgument(3));
                return null;
            }).when(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());
        }

        @SuppressWarnings("unchecked")
        void run() {
            TaskRunner<TradeModel> runner = new TaskRunner<>(trade, (Class<TradeModel>) (Class<?>) Trade.class,
                    () -> completed.set(true), errorMessage::set);
            runner.addTasks(SellerSendsDepositTxAndDelayedPayoutTxMessage.class, SellerPublishesDepositTx.class);
            runner.run();
        }
    }
}

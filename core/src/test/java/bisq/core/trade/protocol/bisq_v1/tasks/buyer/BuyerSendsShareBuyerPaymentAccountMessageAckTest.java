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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.network.MessageState;
import bisq.core.payment.payload.PaymentAccountPayload;
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

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The buyer sends its payment account to the seller until the message arrives at the seller's node or the seller
 * acknowledges it. The task runs in a real TaskRunner with ManualTimer. The ACK of the message has its own message
 * state, the state of the seller's deposit message has nothing to do with it. The task removes its listener of the
 * message state when it ends.
 */
class BuyerSendsShareBuyerPaymentAccountMessageAckTest {
    private final ProcessModel processModel = mock(ProcessModel.class);
    private final Trade trade = mock(Trade.class);
    private final ListenerCountingProperty messageState = new ListenerCountingProperty();
    private final SimpleObjectProperty<MessageState> depositMessageState =
            new SimpleObjectProperty<>(MessageState.UNDEFINED);
    private final MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
    private final AtomicInteger completions = new AtomicInteger();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private final List<SendMailboxMessageListener> listeners = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);

        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getTradingPeerNodeAddress()).thenReturn(new NodeAddress("seller.onion", 9999));
        when(processModel.getOfferId()).thenReturn("trade-id");
        when(processModel.getMyNodeAddress()).thenReturn(new NodeAddress("buyer.onion", 9999));
        when(processModel.getPaymentAccountPayload(trade)).thenReturn(mock(PaymentAccountPayload.class));
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        when(processModel.getShareBuyerPaymentAccountMessageStateProperty()).thenReturn(messageState);
        when(processModel.getDepositTxMessageStateProperty()).thenReturn(depositMessageState);
        TradingPeer tradingPeer = new TradingPeer();
        tradingPeer.setPubKeyRing(new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic()));
        when(processModel.getTradePeer()).thenReturn(tradingPeer);
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
        when(processModel.getP2PService()).thenReturn(p2PService);
        doAnswer(invocation -> {
            listeners.add(invocation.getArgument(3));
            return null;
        }).when(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
    }

    @Test
    void anAckOfTheMessageCompletesTheTask() {
        runTask();
        listeners.get(0).onStoredInMailbox();
        ManualTimer resend = ManualTimer.latest();

        messageState.set(MessageState.ACKNOWLEDGED);

        assertEquals(1, completions.get());
        assertNull(errorMessage.get());
        assertTrue(resend.isStopped());
        assertEquals(0, messageState.numListeners);
    }

    @Test
    void anArrivalCompletesTheTask() {
        runTask();

        listeners.get(0).onArrived();

        assertEquals(1, completions.get());
        assertEquals(0, ManualTimer.count());
        assertEquals(0, messageState.numListeners);
    }

    @Test
    void anAckOfTheDepositMessageDoesNotCompleteTheTask() {
        runTask();
        listeners.get(0).onStoredInMailbox();

        depositMessageState.set(MessageState.ACKNOWLEDGED);

        assertEquals(0, completions.get());
        assertFalse(ManualTimer.latest().isStopped());
    }

    // The listener stays registered for all attempts
    @Test
    void anAckAfterAResendCompletesTheTask() {
        runTask();
        listeners.get(0).onStoredInMailbox();
        ManualTimer.latest().fire();
        listeners.get(1).onStoredInMailbox();
        ManualTimer resend = ManualTimer.latest();

        messageState.set(MessageState.ACKNOWLEDGED);

        assertEquals(1, completions.get());
        assertTrue(resend.isStopped());
    }

    @Test
    void anAckAppliedBeforeTheStartCompletesTheTaskWithoutSending() {
        messageState.set(MessageState.ACKNOWLEDGED);

        runTask();

        assertEquals(1, completions.get());
        assertEquals(0, listeners.size());
    }

    // Without a connection the send reports its fault before it returns
    @Test
    void aFaultReportedAtOnceKeepsTheResend() {
        doAnswer(invocation -> {
            SendMailboxMessageListener listener = invocation.getArgument(3);
            listeners.add(listener);
            listener.onFault("no connection");
            return null;
        }).when(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());

        runTask();

        assertEquals(1, ManualTimer.count());
        assertFalse(ManualTimer.latest().isStopped());
        ManualTimer.latest().fire();
        assertEquals(2, listeners.size());
    }

    // The send that was under way when the ACK came reports its outcome later
    @Test
    void anOutcomeAfterTheAckSchedulesNoResend() {
        runTask();
        messageState.set(MessageState.ACKNOWLEDGED);

        listeners.get(0).onStoredInMailbox();

        assertEquals(1, completions.get());
        assertEquals(0, ManualTimer.count());
    }

    @Test
    void anArrivalAfterTheAckEndsTheTaskOnce() {
        runTask();
        messageState.set(MessageState.ACKNOWLEDGED);

        listeners.get(0).onArrived();

        assertEquals(1, completions.get());
    }

    @Test
    void withoutAckAfterAllAttemptsTheTaskFailsWithItsOwnText() {
        runTask();
        for (int attempt = 0; attempt < 7; attempt++) {
            listeners.get(attempt).onStoredInMailbox();
            ManualTimer.latest().fire();
        }
        listeners.get(7).onStoredInMailbox();

        assertEquals(0, completions.get());
        assertTrue(errorMessage.get().endsWith("when sending our payment account to the peer."));
        assertEquals(0, messageState.numListeners);
    }

    @Test
    void aSendThatThrowsFailsTheTask() {
        doAnswer(invocation -> {
            throw new IllegalStateException("send failed");
        }).when(mailboxMessageService).sendEncryptedMailboxMessage(any(), any(), any(), any());

        runTask();

        assertEquals(0, completions.get());
        assertNotNull(errorMessage.get());
        assertEquals(0, messageState.numListeners);
    }

    @SuppressWarnings("unchecked")
    private void runTask() {
        TaskRunner<TradeModel> runner = new TaskRunner<>(trade, (Class<TradeModel>) (Class<?>) Trade.class,
                completions::incrementAndGet, errorMessage::set);
        runner.addTasks(BuyerSendsShareBuyerPaymentAccountMessage.class);
        runner.run();
    }

    private static final class ListenerCountingProperty extends SimpleObjectProperty<MessageState> {
        private int numListeners;

        private ListenerCountingProperty() {
            super(MessageState.UNDEFINED);
        }

        @Override
        public void addListener(ChangeListener<? super MessageState> listener) {
            numListeners++;
            super.addListener(listener);
        }

        @Override
        public void removeListener(ChangeListener<? super MessageState> listener) {
            numListeners--;
            super.removeListener(listener);
        }
    }
}

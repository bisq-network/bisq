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

package bisq.core.trade.protocol;

import bisq.core.testutil.ManualTimer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.SellerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.TradeMailboxMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.UserThread;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import java.util.ArrayList;
import java.util.List;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeProtocolShutDownTest {
    private static final String TRADE_ID = "trade-id";
    private static final List<WaitingTask> startedTasks = new ArrayList<>();

    // The mock of the abstract SellerTrade has Trade two levels up, as the task runner expects of a trade
    private final SellerTrade trade = mock(SellerTrade.class);
    private final ProcessModel processModel = mock(ProcessModel.class);
    private final P2PService p2PService = mock(P2PService.class);
    private final MailboxMessageService mailboxMessageService = mock(MailboxMessageService.class);
    private TestProtocol protocol;

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        startedTasks.clear();
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getTradeProtocolModel()).thenReturn(processModel);
        when(processModel.getP2PService()).thenReturn(p2PService);
        when(processModel.getTradePeer()).thenReturn(new TradingPeer());
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        when(p2PService.getMailboxMessageService()).thenReturn(mailboxMessageService);
        protocol = new TestProtocol(trade);
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
        ManualTimer.clear();
        startedTasks.clear();
    }

    @Test
    void theShutDownCancelsTheRunningTasksAndStopsTheTimeout() {
        protocol.runWaitingTask();
        ManualTimer timeout = ManualTimer.latest();

        protocol.shutDown();

        assertEquals(1, startedTasks.size());
        assertTrue(startedTasks.get(0).canceled);
        assertTrue(timeout.isStopped());
    }

    @Test
    void aTaskThatFailsAfterTheShutDownSetsNoTradeError() {
        protocol.runWaitingTask();
        startedTasks.get(0).fail();
        verify(trade).setErrorMessage(any());
        protocol.runWaitingTask();

        protocol.shutDown();
        startedTasks.get(1).fail();

        verify(trade).setErrorMessage(any());
    }

    @Test
    void aShutDownProtocolRunsNoTask() {
        protocol.shutDown();

        protocol.runWaitingTask();

        assertEquals(List.of(), startedTasks);
    }

    @Test
    void aShutDownProtocolHandlesNoMessage() {
        DecryptedMessageWithPubKey message = messageForTheTrade();
        protocol.onDirectMessage(message, null);
        protocol.onMailboxMessageAdded(message, null);
        assertEquals(2, protocol.handledMessages.size(), "a message for the trade is handled before the shut down");

        protocol.shutDown();
        protocol.onDirectMessage(message, null);
        protocol.onMailboxMessageAdded(message, null);

        assertEquals(2, protocol.handledMessages.size());
    }

    @Test
    void theShutDownRemovesTheMessageListeners() {
        protocol.onInitialized();
        ManualTimer.latest().fire();

        protocol.shutDown();

        verify(p2PService).removeDecryptedDirectMessageListener(protocol);
        verify(mailboxMessageService).removeDecryptedMailboxListener(protocol);
    }

    // The mailbox listener is added 100 ms after the start, and the trade can leave the pending trades before
    @Test
    void aShutDownBeforeTheMailboxListenerIsAddedKeepsItOff() {
        protocol.onInitialized();
        ManualTimer delayedRegistration = ManualTimer.latest();

        protocol.shutDown();
        delayedRegistration.fire();

        verify(mailboxMessageService, never()).addDecryptedMailboxListener(any());
        verify(mailboxMessageService, never()).runOnceStartupMessagesHandled(any());
    }

    @Test
    void aShutDownBeforeTheStartupMailboxMessagesAreHandledSkipsTheirHandling() {
        protocol.onInitialized();
        ManualTimer.latest().fire();
        ArgumentCaptor<Runnable> startupMessagesHandled = ArgumentCaptor.forClass(Runnable.class);
        verify(mailboxMessageService).runOnceStartupMessagesHandled(startupMessagesHandled.capture());

        protocol.shutDown();
        startupMessagesHandled.getValue().run();

        assertFalse(protocol.startupMailboxMessagesHandled);
    }

    @Test
    void withoutShutDownTheStartupMailboxMessagesAreHandled() {
        protocol.onInitialized();
        ManualTimer.latest().fire();
        ArgumentCaptor<Runnable> startupMessagesHandled = ArgumentCaptor.forClass(Runnable.class);
        verify(mailboxMessageService).runOnceStartupMessagesHandled(startupMessagesHandled.capture());

        startupMessagesHandled.getValue().run();

        assertTrue(protocol.startupMailboxMessagesHandled);
    }

    // The condition is not evaluated, so a failed precondition does not answer the peer either
    @Test
    void aShutDownProtocolEvaluatesNoCondition() {
        protocol.runWithFailingPreCondition();
        assertEquals(1, protocol.preConditionFailures);

        protocol.shutDown();
        protocol.runWithFailingPreCondition();

        assertEquals(1, protocol.preConditionFailures);
    }

    @Test
    void aShutDownProtocolRunsNoRunnable() {
        protocol.runRunnable();
        assertEquals(1, protocol.runnableRuns);

        protocol.shutDown();
        protocol.runRunnable();

        assertEquals(1, protocol.runnableRuns);
    }

    private DecryptedMessageWithPubKey messageForTheTrade() {
        TradeMailboxMessage tradeMessage = mock(TradeMailboxMessage.class);
        when(tradeMessage.getTradeId()).thenReturn(TRADE_ID);
        return new DecryptedMessageWithPubKey(tradeMessage, Sig.generateKeyPair().getPublic());
    }

    // Waits, as a task that waits for a message or a broadcast
    public static class WaitingTask extends TradeTask {
        private boolean canceled;

        public WaitingTask(TaskRunner<Trade> taskHandler, Trade trade) {
            super(taskHandler, trade);
        }

        @Override
        protected void run() {
            startedTasks.add(this);
        }

        @Override
        protected void onCanceled() {
            canceled = true;
        }

        private void fail() {
            failed("failure");
        }
    }

    private static final class TestProtocol extends TradeProtocol {
        private final List<TradeMessage> handledMessages = new ArrayList<>();
        private boolean startupMailboxMessagesHandled;
        private int preConditionFailures;
        private int runnableRuns;

        private enum TestEvent implements FluentProtocol.Event {
            RUN
        }

        private TestProtocol(Trade trade) {
            super(trade);
        }

        private void runWaitingTask() {
            given(preCondition(true).with(TestEvent.RUN))
                    .setup(tasks(WaitingTask.class).withTimeout(120))
                    .executeTasks();
        }

        private void runWithFailingPreCondition() {
            given(new FluentProtocol.Condition(tradeModel)
                    .preCondition(false, () -> preConditionFailures++)
                    .with(TestEvent.RUN))
                    .setup(tasks(WaitingTask.class))
                    .executeTasks();
        }

        private void runRunnable() {
            given(preCondition(true).with(TestEvent.RUN)).run(() -> runnableRuns++);
        }

        @Override
        protected void onStartupMailboxMessagesHandled() {
            startupMailboxMessagesHandled = true;
        }

        @Override
        protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
            handledMessages.add(message);
        }

        @Override
        protected void onMailboxMessage(TradeMessage message, NodeAddress peer) {
            handledMessages.add(message);
        }

        @Override
        protected void onAckMessage(AckMessage ackMessage, NodeAddress peer) {
        }
    }
}

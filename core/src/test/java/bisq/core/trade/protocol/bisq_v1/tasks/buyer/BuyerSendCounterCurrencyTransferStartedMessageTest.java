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
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.TaskRunner;

import javafx.beans.property.SimpleObjectProperty;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the short-circuit in BuyerSendCounterCurrencyTransferStartedMessage#run:
 * when the seller's ACK was already processed while no task instance was listening
 * (it can arrive after a restart, before the task is re-created at startup), the
 * task must not send again but only apply the BUYER_SAW_ARRIVED state transition
 * the ACK would have triggered.
 */
public class BuyerSendCounterCurrencyTransferStartedMessageTest {

    private Trade trade;
    private ProcessModel processModel;
    private SimpleObjectProperty<MessageState> paymentStartedMessageState;
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        trade = mock(Trade.class);
        processModel = mock(ProcessModel.class);
        paymentStartedMessageState = new SimpleObjectProperty<>(MessageState.UNDEFINED);

        when(trade.getProcessModel()).thenReturn(processModel);
        when(processModel.getPaymentStartedMessageStateProperty()).thenReturn(paymentStartedMessageState);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
    }

    @SuppressWarnings("unchecked")
    private void runTask() {
        // same unchecked narrowing as TradeTaskRunner: tasks declare (TaskRunner, Trade)
        // constructors, so the runner must look them up with Trade.class
        TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage::set);
        taskRunner.addTasks(BuyerSendCounterCurrencyTransferStartedMessage.class);
        taskRunner.run();
    }

    @Test
    public void alreadyAcknowledgedMessageIsNotSentAgainButStateIsApplied() {
        paymentStartedMessageState.set(MessageState.ACKNOWLEDGED);

        runTask();

        verify(trade).setStateIfValidTransitionTo(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
        // no send attempt: the wallet (payout address lookup for the message) is never touched
        verify(processModel, never()).getBtcWalletService();
        assertTrue(completed.get());
        assertNull(errorMessage.get());
    }

    @Test
    public void withoutAckTheSendPathIsTaken() {
        paymentStartedMessageState.set(MessageState.SENT);

        runTask();

        // the send path is attempted (and fails in this stripped-down environment,
        // proving the task did not short-circuit) and no ACK state transition happens
        verify(trade, never()).setStateIfValidTransitionTo(any());
        verify(processModel).getBtcWalletService();
        assertFalse(completed.get());
        assertNotNull(errorMessage.get());
    }
}

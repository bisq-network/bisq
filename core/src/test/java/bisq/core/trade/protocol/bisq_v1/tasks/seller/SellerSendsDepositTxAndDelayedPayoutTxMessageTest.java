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

import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.TaskRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class SellerSendsDepositTxAndDelayedPayoutTxMessageTest {
    @Test
    void duplicateDeliveryCallbacksDoNotPersistTheMarkerAgain() {
        ProcessModel processModel = mock(ProcessModel.class);
        TradeManager tradeManager = mock(TradeManager.class);
        AtomicBoolean messageDelivered = new AtomicBoolean();
        when(processModel.isDepositTxAndDelayedPayoutTxMessageDelivered()).thenAnswer(
                invocation -> messageDelivered.get());
        doAnswer(invocation -> {
            messageDelivered.set(invocation.getArgument(0));
            return null;
        }).when(processModel).setDepositTxAndDelayedPayoutTxMessageDelivered(true);
        when(processModel.getTradeManager()).thenReturn(tradeManager);
        Trade trade = mock(Trade.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        TestTask task = new TestTask(mockTaskRunner(), trade);

        task.messageArrived();
        task.messageArrived();

        verify(processModel, times(1)).setDepositTxAndDelayedPayoutTxMessageDelivered(true);
        verify(tradeManager, times(1)).requestPersistence();
    }

    @SuppressWarnings("unchecked")
    private static TaskRunner<Trade> mockTaskRunner() {
        return mock(TaskRunner.class);
    }

    private static final class TestTask extends SellerSendsDepositTxAndDelayedPayoutTxMessage {
        private TestTask(TaskRunner<Trade> taskHandler, Trade trade) {
            super(taskHandler, trade);
        }

        private void messageArrived() {
            setStateArrived();
        }
    }
}

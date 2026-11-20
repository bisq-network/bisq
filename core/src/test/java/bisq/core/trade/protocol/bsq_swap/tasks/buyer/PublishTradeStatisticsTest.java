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

package bisq.core.trade.protocol.bsq_swap.tasks.buyer;

import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;
import bisq.core.trade.statistics.TradeStatistics3;

import bisq.network.p2p.P2PService;

import bisq.common.taskrunner.TaskRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishTradeStatisticsTest {
    @Test
    @SuppressWarnings("unchecked")
    void publishesTradeStatisticsWithoutLookingUpThePeersCapabilities() {
        P2PService p2PService = mock(P2PService.class);
        BsqSwapProtocolModel protocolModel = mock(BsqSwapProtocolModel.class);
        when(protocolModel.getP2PService()).thenReturn(p2PService);
        when(protocolModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        BsqSwapTrade trade = mock(BsqSwapTrade.class);
        when(trade.getBsqSwapProtocolModel()).thenReturn(protocolModel);
        TradeStatistics3 tradeStatistics = mock(TradeStatistics3.class);
        when(tradeStatistics.isValid()).thenReturn(true);
        AtomicBoolean completed = new AtomicBoolean();

        try (MockedStatic<TradeStatistics3> statistics = mockStatic(TradeStatistics3.class)) {
            statistics.when(() -> TradeStatistics3.from(trade)).thenReturn(tradeStatistics);
            TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                    (Class<TradeModel>) (Class<?>) BsqSwapTrade.class,
                    () -> completed.set(true),
                    errorMessage -> {
                    });
            taskRunner.addTasks(PublishTradeStatistics.class);
            taskRunner.run();
        }

        assertTrue(completed.get());
        verify(p2PService).addPersistableNetworkPayload(tradeStatistics, true);
        verify(p2PService, never()).findPeersCapabilities(any());
    }
}

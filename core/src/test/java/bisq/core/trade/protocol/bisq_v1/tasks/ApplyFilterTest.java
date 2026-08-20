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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplyFilterTest {
    @Test
    void persistedPeerAddressIsUsedAfterRestart() {
        ProcessModel processModel = mock(ProcessModel.class);
        FilterPolicyService filterPolicyService = mock(FilterPolicyService.class);
        TradeManager tradeManager = mock(TradeManager.class);
        NodeAddress persistedPeerAddress = mock(NodeAddress.class);
        when(processModel.getTradePeer()).thenReturn(new TradingPeer());
        when(processModel.getFilterPolicyService()).thenReturn(filterPolicyService);
        when(processModel.getTradeManager()).thenReturn(tradeManager);
        Trade trade = mock(Trade.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getTradingPeerNodeAddress()).thenReturn(persistedPeerAddress);
        when(trade.getOffer()).thenReturn(mock(Offer.class));
        AtomicBoolean completed = new AtomicBoolean();
        @SuppressWarnings("unchecked")
        TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage -> {
                });

        taskRunner.addTasks(ApplyFilter.class);
        taskRunner.run();

        assertTrue(completed.get());
        verify(filterPolicyService).isNodeAddressBanned(persistedPeerAddress);
    }
}

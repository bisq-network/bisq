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
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatistics3;

import bisq.network.p2p.P2PService;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;

import java.util.Optional;
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

class SellerPublishesTradeStatisticsTest {
    @Test
    void publishesTradeStatisticsWithoutLookingUpThePeersCapabilities() {
        P2PService p2PService = mock(P2PService.class);
        ReferralIdService referralIdService = mock(ReferralIdService.class);
        when(referralIdService.getOptionalReferralId()).thenReturn(Optional.empty());
        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.getP2PService()).thenReturn(p2PService);
        when(processModel.getReferralIdService()).thenReturn(referralIdService);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        Trade trade = mock(Trade.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.isDepositPublished()).thenReturn(true);
        when(trade.getDepositTx()).thenReturn(new Transaction(MainNetParams.get()));
        TradeStatistics3 tradeStatistics = mock(TradeStatistics3.class);
        when(tradeStatistics.isValid()).thenReturn(true);

        try (MockedStatic<TradeStatistics3> statistics = mockStatic(TradeStatistics3.class)) {
            statistics.when(() -> TradeStatistics3.from(trade, null, false)).thenReturn(tradeStatistics);

            assertTrue(runTask(trade));
        }

        verify(p2PService).addPersistableNetworkPayload(tradeStatistics, true);
        verify(p2PService, never()).findPeersCapabilities(any());
    }

    @SuppressWarnings("unchecked")
    private static boolean runTask(Trade trade) {
        AtomicBoolean completed = new AtomicBoolean();
        TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage -> {
                });
        taskRunner.addTasks(SellerPublishesTradeStatistics.class);
        taskRunner.run();
        return completed.get();
    }
}

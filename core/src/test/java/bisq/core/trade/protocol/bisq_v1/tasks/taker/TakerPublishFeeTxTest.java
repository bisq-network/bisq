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

package bisq.core.trade.protocol.bisq_v1.tasks.taker;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TakerPublishFeeTxTest {
    private final Trade trade = mock(Trade.class);
    private final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
    private TaskRunner<TradeModel> runner;

    @BeforeEach
    void setUp() {
        ProcessModel processModel = mock(ProcessModel.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.isCurrencyForTakerFeeBtc()).thenReturn(true);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
        when(processModel.getTakeOfferFeeTx()).thenReturn(mock(Transaction.class));
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
    }

    @Test
    void aFailedBroadcastSetsTheTradeError() {
        TxBroadcaster.Callback callback = runTask();

        callback.onFailure(new TxBroadcastException("failure"));

        verify(trade, atLeastOnce()).setErrorMessage(any());
    }

    // The trade is moved to the failed trades while the fee tx is broadcast
    @Test
    void aBroadcastThatFailsAfterTheCancelSetsNoTradeError() {
        TxBroadcaster.Callback callback = runTask();

        runner.cancel();
        callback.onFailure(new TxBroadcastException("failure"));

        verify(trade, never()).setErrorMessage(any());
    }

    @SuppressWarnings("unchecked")
    private TxBroadcaster.Callback runTask() {
        runner = new TaskRunner<>(trade, (Class<TradeModel>) (Class<?>) Trade.class, () -> {
        }, errorMessage -> {
        });
        runner.addTasks(TakerPublishFeeTx.class);
        runner.run();
        ArgumentCaptor<TxBroadcaster.Callback> callback = ArgumentCaptor.forClass(TxBroadcaster.Callback.class);
        verify(tradeWalletService).broadcastTx(any(), callback.capture());
        return callback.getValue();
    }
}

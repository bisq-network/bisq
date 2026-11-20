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

package bisq.core.trade.protocol.bisq_v1.tasks.arbitration;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.wallet.Wallet;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishedDelayedPayoutTxTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();
    private static final String TRADE_ID = "trade-id";

    private final BtcWalletService btcWalletService = mock(BtcWalletService.class);
    private final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
    private final Trade trade = mock(Trade.class);
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        Transaction delayedPayoutTx = new Transaction(PARAMS);
        delayedPayoutTx.addInput(new TransactionInput(PARAMS,
                delayedPayoutTx,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.ZERO_HASH)));
        delayedPayoutTx.addOutput(Coin.valueOf(10_000), new ECKey());
        // The wallet has the tx already, so it is not added again
        Wallet wallet = mock(Wallet.class);
        when(wallet.getParams()).thenReturn(PARAMS);
        when(wallet.getTransaction(any())).thenReturn(delayedPayoutTx);
        when(btcWalletService.getWallet()).thenReturn(wallet);

        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.getBtcWalletService()).thenReturn(btcWalletService);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getDelayedPayoutTx()).thenReturn(delayedPayoutTx);
    }

    @Test
    void resetsTheCoinLockOnlyAfterTheBroadcastSucceeded() {
        TxBroadcaster.Callback callback = runTask();

        verify(btcWalletService, never()).resetCoinLockedInMultiSigAddressEntry(any());

        callback.onSuccess(mock(Transaction.class));

        verify(btcWalletService).resetCoinLockedInMultiSigAddressEntry(TRADE_ID);
        assertTrue(completed.get());
    }

    @Test
    void keepsTheCoinLockIfTheBroadcastFailed() {
        TxBroadcaster.Callback callback = runTask();

        callback.onFailure(new TxBroadcastException("broadcast failed"));

        verify(btcWalletService, never()).resetCoinLockedInMultiSigAddressEntry(any());
        assertFalse(completed.get());
        assertNotNull(errorMessage.get());
    }

    @SuppressWarnings("unchecked")
    private TxBroadcaster.Callback runTask() {
        TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage::set);
        taskRunner.addTasks(PublishedDelayedPayoutTx.class);
        taskRunner.run();

        ArgumentCaptor<TxBroadcaster.Callback> callbackCaptor = ArgumentCaptor.forClass(TxBroadcaster.Callback.class);
        verify(tradeWalletService).broadcastTx(any(), callbackCaptor.capture());
        return callbackCaptor.getValue();
    }
}

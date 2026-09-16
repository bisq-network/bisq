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

package bisq.core.btc.wallet;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.dao.DaoCheckpointTestFixture;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerPublishFeeTx;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.wallet.Wallet;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BsqWalletServiceCheckpointTest {
    private final DaoStateMonitoringService monitor = mock(DaoStateMonitoringService.class);
    private final Wallet wallet = mock(Wallet.class);
    private final BsqWalletService walletService = walletService();

    @Test
    void checkpointFailureBlocksOrdinarySigningBeforeWalletOrTransactionAccess() {
        IllegalStateException failure = new IllegalStateException("DAO checkpoint verification failed");
        doThrow(failure).when(monitor).assertCheckpointNotFailed();
        Transaction transaction = mock(Transaction.class);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> walletService.signTxAndVerifyNoDustOutputs(transaction)));

        verifyNoInteractions(wallet, transaction);
    }

    @Test
    void checkpointFailureBlocksSwapSigningBeforeAnyInputIsSigned() {
        IllegalStateException failure = new IllegalStateException("DAO checkpoint verification failed");
        doThrow(failure).when(monitor).assertCheckpointNotFailed();
        Transaction transaction = mock(Transaction.class);
        TransactionInput input = mock(TransactionInput.class);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> walletService.signBsqSwapTransaction(transaction, List.of(input))));

        verifyNoInteractions(wallet, transaction, input);
    }

    @Test
    void checkpointFailureBlocksDirectTradeFeeCommit() {
        IllegalStateException failure = new IllegalStateException("DAO checkpoint verification failed");
        doThrow(failure).when(monitor).assertCheckpointNotFailed();
        Transaction transaction = mock(Transaction.class);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> walletService.commitTx(transaction, TxType.PAY_TRADE_FEE)));

        verifyNoInteractions(wallet, transaction);
    }

    @Test
    void healthyStatePreservesOrdinarySigningAndVerification() throws Exception {
        Transaction transaction = mock(Transaction.class);
        when(wallet.isConsistent()).thenReturn(true);

        assertSame(transaction, walletService.signTxAndVerifyNoDustOutputs(transaction));

        verify(monitor).assertCheckpointNotFailed();
        verify(transaction).verify();
    }

    @Test
    void healthyStateDoesNotRequireBroaderDaoSyncStateForSwapSigning() {
        assertDoesNotThrow(() -> walletService.signBsqSwapTransaction(mock(Transaction.class), List.of()));

        verify(monitor).assertCheckpointNotFailed();
    }

    @Test
    void checkpointFailureBlocksBothDirectBroadcastOverloads() {
        doThrow(new IllegalStateException("DAO checkpoint verification failed"))
                .when(monitor).assertCheckpointNotFailed();
        Transaction transaction = mock(Transaction.class);
        TxBroadcaster.Callback callback = mock(TxBroadcaster.Callback.class);

        try (var broadcaster = mockStatic(TxBroadcaster.class)) {
            assertThrows(IllegalStateException.class, () -> walletService.broadcastTx(transaction, callback));
            assertThrows(IllegalStateException.class, () -> walletService.broadcastTx(transaction, callback, 1));
            broadcaster.verifyNoInteractions();
        }
        verifyNoInteractions(wallet, transaction, callback);
    }

    @Test
    void healthyStatePreservesBothBroadcastOverloads() {
        Transaction transaction = mock(Transaction.class);
        TxBroadcaster.Callback callback = mock(TxBroadcaster.Callback.class);

        try (var broadcaster = mockStatic(TxBroadcaster.class)) {
            walletService.broadcastTx(transaction, callback);
            walletService.broadcastTx(transaction, callback, 1);

            broadcaster.verify(() -> TxBroadcaster.broadcastTx(wallet, null, transaction, callback));
            broadcaster.verify(() -> TxBroadcaster.broadcastTx(wallet, null, transaction, callback, 1));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void actualCheckpointFailureBlocksTheDirectTakerFeePublicationPath() {
        var checkpoint = new DaoCheckpointTestFixture();
        BsqWalletService service = walletService(checkpoint.monitor);
        Trade trade = mock(Trade.class);
        ProcessModel processModel = mock(ProcessModel.class);
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        Transaction transaction = mock(Transaction.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
        when(processModel.getBsqWalletService()).thenReturn(service);
        when(processModel.getTakeOfferFeeTx()).thenReturn(transaction);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<String> error = new AtomicReference<>();
        TaskRunner runner = new TaskRunner(trade, Trade.class, () -> completed.set(true), error::set);
        runner.addTasks(TakerPublishFeeTx.class);

        checkpoint.failCheckpoint();
        runner.run();

        assertFalse(completed.get());
        assertTrue(error.get().contains("DAO checkpoint verification failed"));
        verifyNoInteractions(wallet, tradeWalletService, transaction);
    }

    private BsqWalletService walletService() {
        return walletService(monitor);
    }

    private BsqWalletService walletService(DaoStateMonitoringService monitoringService) {
        BsqWalletService service = new BsqWalletService(mock(WalletsSetup.class), null,
                mock(NonBsqCoinSelector.class), mock(DaoStateService.class), monitoringService,
                null, null, null, null, null);
        service.wallet = wallet;
        return service;
    }
}

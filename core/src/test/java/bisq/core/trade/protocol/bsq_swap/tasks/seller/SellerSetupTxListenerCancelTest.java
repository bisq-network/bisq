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

package bisq.core.trade.protocol.bsq_swap.tasks.seller;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.SellerAsMakerSetupTxListener;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;

import javafx.beans.property.SimpleObjectProperty;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Canceling the task runner removes the swap tx confidence listener from the BSQ wallet service and ends the reaction
 * of the task to the trade state.
 */
class SellerSetupTxListenerCancelTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();

    private final BsqSwapTrade trade = mock(BsqSwapTrade.class);
    private final BsqWalletService walletService = mock(BsqWalletService.class);
    private final SimpleObjectProperty<BsqSwapTrade.State> state =
            new SimpleObjectProperty<>(BsqSwapTrade.State.PREPARATION);
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private TaskRunner<TradeModel> runner;

    @BeforeEach
    void setUp() {
        UserThread.setExecutor(Runnable::run);
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
    }

    private void runTask() {
        Transaction swapTx = new Transaction(PARAMS);
        swapTx.addInput(Sha256Hash.of(new byte[]{1}), 0, new Script(new byte[]{0x51}));
        swapTx.addOutput(Coin.COIN, SegwitAddress.fromKey(PARAMS, new ECKey()));
        BsqSwapProtocolModel protocolModel = mock(BsqSwapProtocolModel.class);
        when(protocolModel.getTransaction()).thenReturn(swapTx);
        when(protocolModel.getBsqWalletService()).thenReturn(walletService);
        when(protocolModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        when(protocolModel.getOpenOfferManager()).thenReturn(mock(OpenOfferManager.class));
        when(trade.getBsqSwapProtocolModel()).thenReturn(protocolModel);
        when(trade.stateProperty()).thenReturn(state);
        when(trade.getOffer()).thenReturn(mock(Offer.class));
        when(walletService.getTransaction(any(Sha256Hash.class))).thenReturn(swapTx);

        runner = createRunner();
        runner.addTasks(SellerAsMakerSetupTxListener.class);
        runner.run();
    }

    @SuppressWarnings("unchecked")
    private TaskRunner<TradeModel> createRunner() {
        return new TaskRunner<>(trade, (Class<TradeModel>) (Class<?>) BsqSwapTrade.class,
                () -> completed.set(true), errorMessage::set);
    }

    private TxConfidenceListener addedListener() {
        ArgumentCaptor<TxConfidenceListener> captor = ArgumentCaptor.forClass(TxConfidenceListener.class);
        verify(walletService).addTxConfidenceListener(captor.capture());
        return captor.getValue();
    }

    private static TransactionConfidence inTheNetwork() {
        TransactionConfidence confidence = mock(TransactionConfidence.class);
        when(confidence.getConfidenceType()).thenReturn(TransactionConfidence.ConfidenceType.PENDING);
        when(confidence.getTransactionHash()).thenReturn(Sha256Hash.of(new byte[]{2}));
        return confidence;
    }

    @Test
    void withoutCancelTheSwapTxInTheNetworkCompletesTheTradeAndRemovesTheListener() {
        runTask();
        TxConfidenceListener listener = addedListener();
        assertTrue(completed.get());
        assertNull(errorMessage.get());

        listener.onTransactionConfidenceChanged(inTheNetwork());

        verify(trade).setState(BsqSwapTrade.State.COMPLETED);
        verify(walletService).removeTxConfidenceListener(same(listener));
    }

    @Test
    void aCanceledTaskRemovesItsListenerAndNoLongerReactsToTheTradeState() {
        runTask();
        TxConfidenceListener listener = addedListener();
        verify(walletService, never()).removeTxConfidenceListener(any());

        runner.cancel();
        verify(walletService, times(1)).removeTxConfidenceListener(same(listener));
        state.set(BsqSwapTrade.State.COMPLETED);

        verify(walletService, times(1)).removeTxConfidenceListener(any());
    }
}

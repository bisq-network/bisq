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

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.params.MainNetParams;

import javafx.beans.property.SimpleObjectProperty;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.ArgumentCaptor;

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
 * Canceling the task runner removes the payout tx confidence listener from the wallet service and ends the reaction
 * of the task to the trade state.
 */
class BuyerSetupPayoutTxListenerCancelTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();

    private final Trade trade = mock(Trade.class);
    private final ProcessModel processModel = mock(ProcessModel.class);
    private final BtcWalletService walletService = mock(BtcWalletService.class);
    private final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
    private final SimpleObjectProperty<Trade.State> state = new SimpleObjectProperty<>(Trade.State.PREPARATION);
    private final AtomicBoolean payoutPublished = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private TaskRunner<TradeModel> runner;

    @SuppressWarnings("unchecked")
    private void runTask() {
        AddressEntry payoutEntry = mock(AddressEntry.class);
        when(payoutEntry.getAddress()).thenReturn(SegwitAddress.fromKey(PARAMS, new ECKey()));
        when(walletService.getAddressEntry("trade-id", AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(Optional.of(payoutEntry));
        Transaction depositTx = mock(Transaction.class);
        when(depositTx.getConfidence()).thenReturn(mock(TransactionConfidence.class));
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn("trade-id");
        when(trade.getId()).thenReturn("trade-id");
        when(trade.getDepositTx()).thenReturn(depositTx);
        when(trade.stateProperty()).thenReturn(state);
        when(trade.isPayoutPublished()).thenAnswer(i -> payoutPublished.get());
        when(trade.getProcessModel()).thenReturn(processModel);
        when(processModel.getOffer()).thenReturn(offer);
        when(processModel.getBtcWalletService()).thenReturn(walletService);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));

        runner = new TaskRunner<>(trade, (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true), errorMessage::set);
        runner.addTasks(BuyerSetupPayoutTxListener.class);
        runner.run();
    }

    private AddressConfidenceListener addedListener() {
        ArgumentCaptor<AddressConfidenceListener> captor = ArgumentCaptor.forClass(AddressConfidenceListener.class);
        verify(walletService).addAddressConfidenceListener(captor.capture());
        return captor.getValue();
    }

    @Test
    void withoutCancelAPayoutPublishedStateResetsTheMultiSigEntryAndRemovesTheListener() {
        runTask();
        AddressConfidenceListener listener = addedListener();
        assertTrue(completed.get());
        assertNull(errorMessage.get());

        payoutPublished.set(true);
        state.set(Trade.State.BUYER_SAW_PAYOUT_TX_IN_NETWORK);

        verify(walletService).resetCoinLockedInMultiSigAddressEntry("trade-id");
        verify(walletService).removeAddressConfidenceListener(same(listener));
    }

    @Test
    void withoutCancelTheListenerReactsToAPayoutTxInTheNetwork() {
        runTask();
        AddressConfidenceListener listener = addedListener();
        TransactionConfidence confidence = mock(TransactionConfidence.class);
        when(confidence.getConfidenceType()).thenReturn(TransactionConfidence.ConfidenceType.PENDING);
        when(confidence.getTransactionHash()).thenReturn(Sha256Hash.ZERO_HASH);

        listener.onTransactionConfidenceChanged(confidence);

        verify(tradeWalletService).getWalletTx(Sha256Hash.ZERO_HASH);
    }

    @Test
    void aCanceledTaskRemovesItsListenerAndNoLongerReactsToTheTradeState() {
        runTask();
        AddressConfidenceListener listener = addedListener();
        verify(walletService, never()).removeAddressConfidenceListener(any());

        runner.cancel();
        verify(walletService, times(1)).removeAddressConfidenceListener(same(listener));
        payoutPublished.set(true);
        state.set(Trade.State.BUYER_SAW_PAYOUT_TX_IN_NETWORK);

        verify(walletService, never()).resetCoinLockedInMultiSigAddressEntry(any());
        verify(walletService, times(1)).removeAddressConfidenceListener(any());
    }
}

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
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;

import javafx.beans.property.SimpleObjectProperty;

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
 * Canceling the task runner removes the deposit tx confidence listener from the wallet service and ends the reaction
 * of the task to the trade state.
 */
class BuyerSetupDepositTxListenerCancelTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();

    private final Trade trade = mock(Trade.class);
    private final ProcessModel processModel = mock(ProcessModel.class);
    private final BtcWalletService walletService = mock(BtcWalletService.class);
    private final SimpleObjectProperty<Trade.State> state = new SimpleObjectProperty<>(Trade.State.PREPARATION);
    private final AtomicBoolean depositPublished = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private TaskRunner<TradeModel> runner;

    private void runTask() {
        Transaction preparedDepositTx = new Transaction(PARAMS);
        preparedDepositTx.addInput(Sha256Hash.of(new byte[]{1}), 0, new Script(new byte[]{0x51}));
        preparedDepositTx.addOutput(Coin.COIN, SegwitAddress.fromKey(PARAMS, new ECKey()));
        Offer offer = mock(Offer.class);
        when(offer.getOfferFeePaymentTxId()).thenReturn("maker-fee-tx-id");
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getId()).thenReturn("trade-id");
        when(trade.stateProperty()).thenReturn(state);
        when(trade.isDepositPublished()).thenAnswer(i -> depositPublished.get());
        when(processModel.getBtcWalletService()).thenReturn(walletService);
        when(processModel.getPreparedDepositTx()).thenReturn(preparedDepositTx.bitcoinSerialize());
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        when(trade.getProcessModel()).thenReturn(processModel);
        when(walletService.getParams()).thenReturn(PARAMS);

        runner = createRunner();
        runner.addTasks(BuyerSetupDepositTxListener.class);
        runner.run();
    }

    @SuppressWarnings("unchecked")
    private TaskRunner<TradeModel> createRunner() {
        return new TaskRunner<>(trade, (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true), errorMessage::set);
    }

    private AddressConfidenceListener addedListener() {
        ArgumentCaptor<AddressConfidenceListener> captor = ArgumentCaptor.forClass(AddressConfidenceListener.class);
        verify(walletService).addAddressConfidenceListener(captor.capture());
        return captor.getValue();
    }

    @Test
    void withoutCancelADepositPublishedStateMakesTheReservedEntryAvailableAndRemovesTheListener() {
        runTask();
        AddressConfidenceListener listener = addedListener();
        assertTrue(completed.get());
        assertNull(errorMessage.get());

        depositPublished.set(true);
        state.set(Trade.State.BUYER_SAW_DEPOSIT_TX_IN_NETWORK);

        verify(walletService).swapTradeEntryToAvailableEntry("trade-id", AddressEntry.Context.RESERVED_FOR_TRADE);
        verify(walletService).removeAddressConfidenceListener(same(listener));
    }

    @Test
    void aCanceledTaskRemovesItsListenerAndNoLongerReactsToTheTradeState() {
        runTask();
        AddressConfidenceListener listener = addedListener();
        verify(walletService, never()).removeAddressConfidenceListener(any());

        runner.cancel();
        verify(walletService, times(1)).removeAddressConfidenceListener(same(listener));
        depositPublished.set(true);
        state.set(Trade.State.BUYER_SAW_DEPOSIT_TX_IN_NETWORK);

        verify(walletService, never()).swapTradeEntryToAvailableEntry(any(), any());
        verify(walletService, times(1)).removeAddressConfidenceListener(any());
    }
}

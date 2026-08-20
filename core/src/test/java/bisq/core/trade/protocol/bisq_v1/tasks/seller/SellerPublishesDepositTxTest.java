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

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerPublishesDepositTxTest {
    @Test
    void fiatDepositIsNotBroadcastBeforeBuyerAccountValidation() {
        ProcessModel processModel = processModel(false, true);
        TaskResult result = runTask(trade(processModel));

        assertTrue(result.completed());
        assertNull(result.errorMessage());
        verify(processModel, never()).getTradeWalletService();
    }

    @Test
    void fiatDepositIsNotBroadcastBeforeDepositMessageDelivery() {
        ProcessModel processModel = processModel(true, false);
        TaskResult result = runTask(trade(processModel));

        assertTrue(result.completed());
        assertNull(result.errorMessage());
        verify(processModel, never()).getTradeWalletService();
    }

    @Test
    void fiatDepositIsBroadcastWhenBothGatesComplete() {
        ProcessModel processModel = processModel(true, true);
        Transaction depositTx = new Transaction(MainNetParams.get());
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        when(processModel.getDepositTx()).thenReturn(depositTx);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);

        TaskResult result = runTask(trade(processModel));

        assertFalse(result.completed());
        assertNull(result.errorMessage());
        verify(tradeWalletService).broadcastTx(same(depositTx), any(TxBroadcaster.Callback.class));
    }

    @Test
    void anOverlappingCryptoTaskRunnerDoesNotBroadcastTheDepositASecondTime() {
        ProcessModel processModel = processModel(true, true);
        Transaction depositTx = new Transaction(MainNetParams.get());
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        when(processModel.getDepositTx()).thenReturn(depositTx);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
        when(processModel.getBtcWalletService()).thenReturn(mock(BtcWalletService.class));
        Trade trade = trade(processModel, false);

        // The callback of the first broadcast stays pending, so the trade phase does not advance yet.
        TaskResult firstResult = runTask(trade);
        TaskResult overlappingResult = runTask(trade);

        assertTrue(overlappingResult.completed());
        assertNull(overlappingResult.errorMessage());
        ArgumentCaptor<TxBroadcaster.Callback> callback = ArgumentCaptor.forClass(TxBroadcaster.Callback.class);
        verify(tradeWalletService).broadcastTx(same(depositTx), callback.capture());

        callback.getValue().onSuccess(depositTx);

        assertTrue(firstResult.completed());
        verify(trade).applyDepositTx(same(depositTx));

        // The callback releases the guard. The mock deliberately keeps the persisted phase unpublished so a third
        // task proves that publication can be retried after the in-flight operation ended.
        runTask(trade);
        verify(tradeWalletService, times(2)).broadcastTx(same(depositTx), any(TxBroadcaster.Callback.class));
    }

    @Test
    void aFailedBroadcastReleasesTheGuardSoPublicationCanBeRetried() {
        ProcessModel processModel = processModel(true, true);
        Transaction depositTx = new Transaction(MainNetParams.get());
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        when(processModel.getDepositTx()).thenReturn(depositTx);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
        Trade trade = trade(processModel);

        runTask(trade);
        ArgumentCaptor<TxBroadcaster.Callback> callback = ArgumentCaptor.forClass(TxBroadcaster.Callback.class);
        verify(tradeWalletService).broadcastTx(same(depositTx), callback.capture());
        callback.getValue().onFailure(new TxBroadcastException("broadcast failed"));

        runTask(trade);

        verify(tradeWalletService, times(2)).broadcastTx(same(depositTx), any(TxBroadcaster.Callback.class));
    }

    private static ProcessModel processModel(boolean accountValidated, boolean messageDelivered) {
        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.isBuyerPaymentAccountValidated()).thenReturn(accountValidated);
        when(processModel.isDepositTxAndDelayedPayoutTxMessageDelivered()).thenReturn(messageDelivered);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        // The single-flight guard is trade state, so we delegate it to a real ProcessModel instead of
        // reimplementing it in the test.
        ProcessModel guard = new ProcessModel("offer-id", "account-id", pubKeyRing());
        when(processModel.tryStartDepositTxBroadcast()).thenAnswer(invocation -> guard.tryStartDepositTxBroadcast());
        doAnswer(invocation -> {
            guard.finishDepositTxBroadcast();
            return null;
        }).when(processModel).finishDepositTxBroadcast();
        return processModel;
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
    }

    private static Trade trade(ProcessModel processModel) {
        return trade(processModel, true);
    }

    private static Trade trade(ProcessModel processModel, boolean isFiatOffer) {
        Offer offer = mock(Offer.class);
        when(offer.isFiatOffer()).thenReturn(isFiatOffer);
        when(offer.getId()).thenReturn("offer-id");
        when(processModel.getOffer()).thenReturn(offer);
        Trade trade = mock(Trade.class);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getProcessModel()).thenReturn(processModel);
        return trade;
    }

    @SuppressWarnings("unchecked")
    private static TaskResult runTask(Trade trade) {
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage::set);
        taskRunner.addTasks(SellerPublishesDepositTx.class);
        taskRunner.run();
        return new TaskResult(completed, errorMessage);
    }

    private record TaskResult(AtomicBoolean completedState, AtomicReference<String> errorMessageState) {
        private boolean completed() {
            return completedState.get();
        }

        private String errorMessage() {
            return errorMessageState.get();
        }
    }
}

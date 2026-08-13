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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerVerifyBuyerPaymentAccountTest {
    @Test
    void missingFiatAccountFailsClosed() {
        ProcessModel processModel = processModel(false);
        Trade trade = trade("EUR", processModel);

        TaskResult result = runTask(trade);

        assertFalse(result.completed());
        assertNotNull(result.errorMessage());
        verify(processModel, never()).setBuyerPaymentAccountValidated(true);
    }

    @Test
    void persistedValidationAllowsSettlementAfterRestart() {
        ProcessModel processModel = processModel(true);
        Trade trade = trade("EUR", processModel);

        TaskResult result = runTask(trade);

        assertTrue(result.completed());
        assertNull(result.errorMessage());
        verify(processModel, never()).getAccountAgeWitnessService();
    }

    @Test
    void preUpgradeTradeReconstructsValidationFromPersistedReveal() {
        ProcessModel processModel = processModel(false);
        TradingPeer tradingPeer = new TradingPeer();
        PaymentAccountPayload paymentAccountPayload = mock(PaymentAccountPayload.class);
        byte[] paymentAccountHash = {3};
        when(paymentAccountPayload.getHashForContract()).thenReturn(paymentAccountHash);
        PubKeyRing pubKeyRing = mock(PubKeyRing.class);
        byte[] nonce = {1};
        byte[] signature = {2};
        tradingPeer.setPaymentAccountPayload(paymentAccountPayload);
        tradingPeer.setPubKeyRing(pubKeyRing);
        tradingPeer.setAccountAgeWitnessNonce(nonce);
        tradingPeer.setAccountAgeWitnessSignature(signature);
        tradingPeer.setCurrentDate(1234L);
        when(processModel.getTradePeer()).thenReturn(tradingPeer);
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        when(processModel.getAccountAgeWitnessService()).thenReturn(accountAgeWitnessService);
        when(accountAgeWitnessService.verifyAccountAgeWitnessAtTradeDate(
                any(),
                eq(paymentAccountPayload),
                any(),
                eq(pubKeyRing),
                same(nonce),
                same(signature),
                any())).thenReturn(true);
        Trade trade = trade("EUR", processModel);
        Contract contract = mock(Contract.class);
        when(trade.getContract()).thenReturn(contract);
        when(contract.getHashOfPeersPaymentAccountPayload(processModel.getPubKeyRing()))
                .thenReturn(paymentAccountHash);

        TaskResult result = runTask(trade);

        assertTrue(result.completed());
        assertNull(result.errorMessage());
        verify(processModel).setBuyerPaymentAccountValidated(true);
        verify(processModel.getTradeManager(), atLeastOnce()).requestPersistence();
    }

    @Test
    void cryptoTradeDoesNotRequireAccountAgeValidation() {
        ProcessModel processModel = processModel(false);
        Trade trade = trade("XMR", processModel);

        TaskResult result = runTask(trade);

        assertTrue(result.completed());
        assertNull(result.errorMessage());
        verify(processModel, never()).getAccountAgeWitnessService();
    }

    private static ProcessModel processModel(boolean validated) {
        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.isBuyerPaymentAccountValidated()).thenReturn(validated);
        when(processModel.getTradePeer()).thenReturn(new TradingPeer());
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        return processModel;
    }

    private static Trade trade(String currencyCode, ProcessModel processModel) {
        Offer offer = mock(Offer.class);
        when(offer.getCurrencyCode()).thenReturn(currencyCode);
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
        taskRunner.addTasks(SellerVerifyBuyerPaymentAccount.class);
        taskRunner.run();
        return new TaskResult(completed.get(), errorMessage.get());
    }

    private record TaskResult(boolean completed, String errorMessage) {
    }
}

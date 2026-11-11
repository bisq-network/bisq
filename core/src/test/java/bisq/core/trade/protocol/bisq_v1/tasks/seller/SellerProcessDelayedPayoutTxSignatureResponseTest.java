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

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.DelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * The seller stores its finalized deposit tx when it processes the buyer's signature of the delayed payout tx, so
 * that a restart before the deposit is published can resume the delivery of the deposit message and the publication.
 */
class SellerProcessDelayedPayoutTxSignatureResponseTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void theFinalizedDepositTxIsStoredForFiatAndCryptoOffers(boolean isFiatOffer) {
        Context.propagate(new Context(PARAMS));
        Transaction myDepositTx = new Transaction(PARAMS);
        myDepositTx.addInput(Sha256Hash.of(new byte[]{1}), 0, new Script(new byte[0]));
        myDepositTx.addOutput(Coin.COIN, SegwitAddress.fromKey(PARAMS, new ECKey()));

        DelayedPayoutTxSignatureResponse response = mock(DelayedPayoutTxSignatureResponse.class);
        when(response.getDelayedPayoutTxBuyerSignature())
                .thenReturn(new ECKey().sign(Sha256Hash.of(new byte[]{2})).encodeToDER());
        when(response.getDepositTx()).thenReturn(myDepositTx.bitcoinSerialize());

        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        ProcessModel processModel = spy(new ProcessModel("offer-id", "account-id", mock(PubKeyRing.class)));
        doReturn(response).when(processModel).getTradeMessage();
        doReturn(mock(TradeWalletService.class)).when(processModel).getTradeWalletService();
        doReturn(btcWalletService).when(processModel).getBtcWalletService();
        doReturn(mock(TradeManager.class)).when(processModel).getTradeManager();
        doReturn(myDepositTx).when(processModel).getDepositTx();
        processModel.setRawTransactionInputs(List.of(mock(RawTransactionInput.class)));
        processModel.getTradePeer().setRawTransactionInputs(List.of(mock(RawTransactionInput.class)));

        Offer offer = mock(Offer.class);
        when(offer.isFiatOffer()).thenReturn(isFiatOffer);
        SellerAsTakerTrade trade = mock(SellerAsTakerTrade.class);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getProcessModel()).thenReturn(processModel);

        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        runTask(trade, completed, errorMessage);

        assertNull(errorMessage.get());
        assertTrue(completed.get());
        assertArrayEquals(myDepositTx.bitcoinSerialize(), processModel.getFinalizedDepositTx());
    }

    @SuppressWarnings("unchecked")
    private static void runTask(Trade trade, AtomicBoolean completed, AtomicReference<String> errorMessage) {
        TaskRunner<TradeModel> runner = new TaskRunner<>(trade, (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true), errorMessage::set);
        runner.addTasks(SellerProcessDelayedPayoutTxSignatureResponse.class);
        runner.run();
    }
}

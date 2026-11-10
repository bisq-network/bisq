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
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import javafx.beans.property.SimpleObjectProperty;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Covers the binding of a deposit tx seen in the network to our trade (report7b): only a tx that spends one
// maker fee output and one taker fee output and pays to our deposit address is taken as our deposit tx.
class BuyerSetupDepositTxListenerTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();

    private final Script depositScript = ScriptBuilder.createP2WPKHOutputScript(new ECKey());
    private final Address depositAddress = depositScript.getToAddress(PARAMS);
    private Transaction makerFeeTx;
    private Transaction takerFeeTx;
    private BtcWalletService btcWalletService;
    private TradeWalletService tradeWalletService;
    private Trade trade;

    @BeforeEach
    void setUp() {
        Context.propagate(new Context(PARAMS));
        makerFeeTx = feeTx(2);
        takerFeeTx = feeTx(1);
        btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        tradeWalletService = mock(TradeWalletService.class);
    }

    @Test
    void appliesTxThatSpendsMakerAndTakerFeeOutputsAndPaysToDepositAddress() {
        Transaction depositTx = depositTx(input(makerFeeTx, 0), input(takerFeeTx, 0));
        setUpTrade(takerFeeTx.getTxId().toString(), depositTx);

        assertTrue(runTask());

        verify(trade).applyDepositTx(depositTx);
        verify(trade).setState(Trade.State.BUYER_SAW_DEPOSIT_TX_IN_NETWORK);
    }

    @Test
    void doesNotApplyTxThatSpendsTwoMakerFeeOutputsWithoutTakerFeeInput() {
        Transaction fakeDepositTx = depositTx(input(makerFeeTx, 0), input(makerFeeTx, 1));
        setUpTrade(takerFeeTx.getTxId().toString(), fakeDepositTx);

        assertTrue(runTask());

        verify(trade, never()).applyDepositTx(any());
        verify(btcWalletService).addAddressConfidenceListener(any());
    }

    @Test
    void doesNotApplyTxThatDoesNotPayToDepositAddress() {
        Transaction tx = transaction(input(makerFeeTx, 0), input(takerFeeTx, 0));
        tx.addOutput(Coin.valueOf(20_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        setUpTrade(takerFeeTx.getTxId().toString(), tx);

        assertTrue(runTask());

        verify(trade, never()).applyDepositTx(any());
    }

    @Test
    void doesNotApplyTxWithNonCanonicalFields() {
        Transaction tx = depositTx(input(makerFeeTx, 0), input(takerFeeTx, 0));
        tx.setVersion(2);
        setUpTrade(takerFeeTx.getTxId().toString(), tx);

        assertTrue(runTask());

        verify(trade, never()).applyDepositTx(any());
    }

    @Test
    void doesNotApplyTxThatIsNotVisibleInNetwork() {
        Transaction depositTx = depositTx(input(makerFeeTx, 0), input(takerFeeTx, 0));
        setUpTrade(takerFeeTx.getTxId().toString(), depositTx);
        depositTx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.DEAD);

        assertTrue(runTask());

        verify(trade, never()).applyDepositTx(any());
    }

    @Test
    void buyerAsMakerRequiresAMakerFeeInput() {
        Transaction withoutMakerFeeInput = depositTx(input(takerFeeTx, 0));
        setUpTrade(null, withoutMakerFeeInput);

        assertTrue(runTask());

        verify(trade, never()).applyDepositTx(any());
    }

    @Test
    void buyerAsMakerAppliesTxThatSpendsAMakerFeeOutput() {
        Transaction depositTx = depositTx(input(makerFeeTx, 0), input(takerFeeTx, 0));
        setUpTrade(null, depositTx);

        assertTrue(runTask());

        verify(trade).applyDepositTx(depositTx);
    }

    @Test
    void listenerAppliesTxOnlyWhenAMatchingTxShowsUp() {
        Transaction fakeDepositTx = depositTx(input(makerFeeTx, 0), input(makerFeeTx, 1));
        setUpTrade(takerFeeTx.getTxId().toString(), fakeDepositTx);
        assertTrue(runTask());
        ArgumentCaptor<AddressConfidenceListener> listener = ArgumentCaptor.forClass(AddressConfidenceListener.class);
        verify(btcWalletService).addAddressConfidenceListener(listener.capture());

        listener.getValue().onTransactionConfidenceChanged(fakeDepositTx.getConfidence());
        verify(trade, never()).applyDepositTx(any());

        Transaction depositTx = depositTx(input(makerFeeTx, 0), input(takerFeeTx, 0));
        depositTx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        when(tradeWalletService.getWalletTx(depositTx.getTxId())).thenReturn(depositTx);
        listener.getValue().onTransactionConfidenceChanged(depositTx.getConfidence());

        verify(trade).applyDepositTx(depositTx);
        verify(trade).setState(Trade.State.BUYER_SAW_DEPOSIT_TX_IN_NETWORK);
    }

    private void setUpTrade(String takerFeeTxId, Transaction txSeenAtDepositAddress) {
        // With an input, so that the serialized tx does not start like a segwit marker.
        Transaction preparedDepositTx = depositTx(input(makerFeeTx, 0));
        TransactionConfidence confidence = txSeenAtDepositAddress.getConfidence();
        confidence.setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        when(btcWalletService.getConfidenceForAddress(depositAddress)).thenReturn(confidence);
        when(tradeWalletService.getWalletTx(txSeenAtDepositAddress.getTxId())).thenReturn(txSeenAtDepositAddress);

        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.getBtcWalletService()).thenReturn(btcWalletService);
        when(processModel.getTradeWalletService()).thenReturn(tradeWalletService);
        when(processModel.getPreparedDepositTx()).thenReturn(preparedDepositTx.bitcoinSerialize());
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        Offer offer = mock(Offer.class);
        when(offer.getOfferFeePaymentTxId()).thenReturn(makerFeeTx.getTxId().toString());
        trade = mock(Trade.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getTakerFeeTxId()).thenReturn(takerFeeTxId);
        when(trade.getId()).thenReturn("trade-id");
        when(trade.stateProperty()).thenReturn(new SimpleObjectProperty<>(Trade.State.PREPARATION));
    }

    @SuppressWarnings("unchecked")
    private boolean runTask() {
        AtomicBoolean completed = new AtomicBoolean();
        TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage -> {
                });
        taskRunner.addTasks(BuyerSetupDepositTxListener.class);
        taskRunner.run();
        return completed.get();
    }

    private Transaction depositTx(TransactionOutPoint... inputs) {
        Transaction tx = transaction(inputs);
        tx.addOutput(Coin.valueOf(20_000), depositScript);
        return tx;
    }

    private static Transaction transaction(TransactionOutPoint... inputs) {
        Transaction tx = new Transaction(PARAMS);
        for (TransactionOutPoint outPoint : inputs) {
            tx.addInput(new TransactionInput(PARAMS, tx, new byte[0], outPoint));
        }
        return tx;
    }

    private static TransactionOutPoint input(Transaction parent, int index) {
        return new TransactionOutPoint(PARAMS, index, parent.getTxId());
    }

    private static Transaction feeTx(int numOutputs) {
        Transaction tx = new Transaction(PARAMS);
        for (int i = 0; i < numOutputs; i++) {
            tx.addOutput(Coin.valueOf(10_000 + i), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        }
        return tx;
    }
}

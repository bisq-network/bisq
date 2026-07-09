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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.protocol.bsq_swap.tasks.seller;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapTxInputsMessage;
import bisq.core.trade.protocol.bsq_swap.messages.BuyersBsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.ProcessBuyersBsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.SellerAsMakerCreatesAndSignsTx;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.ProcessBsqSwapTxInputsMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.SellerAsTakerCreatesAndSignsTx;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.params.MainNetParams;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BsqSwapSellerDaoStateGuardTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final NodeAddress NODE_ADDRESS = new NodeAddress("peer.onion:8000");
    private static final long BTC_TRADE_AMOUNT = 100_000;
    private static final long BSQ_TRADE_AMOUNT = 100_000;
    private static final long MAKER_FEE = 10;
    private static final long TAKER_FEE = 10;
    private static final long TX_FEE_PER_VBYTE = 1;

    @Test
    void sellerAsMakerRejectsBuyerBsqInputsWhenDaoStateIsNotInSync() {
        RawTransactionInput buyerBsqInput = rawInput(BSQ_TRADE_AMOUNT + TAKER_FEE);
        Fixture fixture = fixture();
        when(fixture.btcWalletService.getTxFromSerializedTx(any())).thenReturn(parentTx(buyerBsqInput));
        when(fixture.daoFacade.isDaoStateReadyAndInSync()).thenReturn(false);
        fixture.protocolModel.setTradeMessage(new BuyersBsqSwapRequest("trade-id",
                NODE_ADDRESS,
                mock(PubKeyRing.class),
                BTC_TRADE_AMOUNT,
                TX_FEE_PER_VBYTE,
                MAKER_FEE,
                TAKER_FEE,
                1,
                List.of(buyerBsqInput),
                0,
                addressString(),
                addressString()));

        TaskResult result = runTask(fixture.trade, ProcessBuyersBsqSwapRequest.class);

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("DAO state is not ready and in sync"));
        verify(fixture.daoFacade, never()).isTxOutputSpendable(any());
    }

    @Test
    void sellerAsTakerRejectsBuyerBsqInputsWhenDaoStateIsNotInSync() {
        RawTransactionInput buyerBsqInput = rawInput(BSQ_TRADE_AMOUNT + MAKER_FEE);
        Fixture fixture = fixture();
        when(fixture.btcWalletService.getTxFromSerializedTx(any())).thenReturn(parentTx(buyerBsqInput));
        when(fixture.daoFacade.isDaoStateReadyAndInSync()).thenReturn(false);
        fixture.protocolModel.setTradeMessage(new BsqSwapTxInputsMessage("trade-id",
                NODE_ADDRESS,
                List.of(buyerBsqInput),
                0,
                addressString(),
                addressString()));

        TaskResult result = runTask(fixture.trade, ProcessBsqSwapTxInputsMessage.class);

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("DAO state is not ready and in sync"));
        verify(fixture.daoFacade, never()).isTxOutputSpendable(any());
    }

    @Test
    void sellerAsMakerRejectsBeforeSigningWhenDaoStateIsNotInSync() throws Exception {
        assertSigningBlocked(SellerAsMakerCreatesAndSignsTx.class, MAKER_FEE);
    }

    @Test
    void sellerAsTakerRejectsBeforeSigningWhenDaoStateIsNotInSync() throws Exception {
        assertSigningBlocked(SellerAsTakerCreatesAndSignsTx.class, TAKER_FEE);
    }

    private static void assertSigningBlocked(Class<? extends bisq.common.taskrunner.Task> taskClass,
                                             long sellersTradeFee) throws Exception {
        RawTransactionInput buyerBsqInput = rawInput(BSQ_TRADE_AMOUNT);
        RawTransactionInput sellerBtcInput = rawInput(BTC_TRADE_AMOUNT);
        Fixture fixture = fixture();
        fixture.protocolModel.getTradePeer().setInputs(List.of(buyerBsqInput));
        fixture.protocolModel.getTradePeer().setChange(0);
        fixture.protocolModel.getTradePeer().setBtcAddress(addressString());
        fixture.protocolModel.setPayout(BSQ_TRADE_AMOUNT - sellersTradeFee);

        AddressEntry changeAddressEntry = mock(AddressEntry.class);
        when(changeAddressEntry.getAddressString()).thenReturn(addressString());
        when(fixture.btcWalletService.getFreshAddressEntry()).thenReturn(changeAddressEntry);
        when(fixture.btcWalletService.getInputsAndChange(any(Coin.class)))
                .thenReturn(new Tuple2<>(List.of(sellerBtcInput), Coin.ZERO));
        when(fixture.bsqWalletService.getUnusedAddress()).thenReturn(Address.fromString(PARAMS, addressString()));
        when(fixture.tradeWalletService.sellerBuildBsqSwapTx(anyList(),
                anyList(),
                any(Coin.class),
                any(),
                any(Coin.class),
                any(),
                any(Coin.class),
                any(),
                any(Coin.class),
                any()))
                .thenReturn(unsignedSwapTx(buyerBsqInput, sellerBtcInput));
        when(fixture.daoFacade.isDaoStateReadyAndInSync()).thenReturn(false);

        TaskResult result = runTask(fixture.trade, taskClass);

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("DAO state is not ready and in sync"));
        verify(fixture.tradeWalletService, never()).signBsqSwapTransaction(any(Transaction.class), anyList());
    }

    private static Fixture fixture() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        BsqWalletService bsqWalletService = mock(BsqWalletService.class);
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        DaoFacade daoFacade = mock(DaoFacade.class);
        Provider provider = mock(Provider.class);
        TradeManager tradeManager = mock(TradeManager.class);
        Offer offer = mock(Offer.class);

        when(provider.getBtcWalletService()).thenReturn(btcWalletService);
        when(provider.getBsqWalletService()).thenReturn(bsqWalletService);
        when(provider.getTradeWalletService()).thenReturn(tradeWalletService);
        when(provider.getDaoFacade()).thenReturn(daoFacade);

        BsqSwapProtocolModel protocolModel = new BsqSwapProtocolModel(mock(PubKeyRing.class));
        protocolModel.applyTransient(provider, tradeManager, offer);

        BsqSwapTrade trade = mock(BsqSwapTrade.class);
        when(trade.getBsqSwapProtocolModel()).thenReturn(protocolModel);
        when(trade.getAmountAsLong()).thenReturn(BTC_TRADE_AMOUNT);
        when(trade.getBsqTradeAmount()).thenReturn(BSQ_TRADE_AMOUNT);
        when(trade.getMakerFeeAsLong()).thenReturn(MAKER_FEE);
        when(trade.getTakerFeeAsLong()).thenReturn(TAKER_FEE);
        when(trade.getTxFeePerVbyte()).thenReturn(TX_FEE_PER_VBYTE);

        return new Fixture(trade,
                protocolModel,
                btcWalletService,
                bsqWalletService,
                tradeWalletService,
                daoFacade);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TaskResult runTask(BsqSwapTrade trade,
                                      Class<? extends bisq.common.taskrunner.Task> taskClass) {
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        TaskRunner taskRunner = new TaskRunner(trade,
                BsqSwapTrade.class,
                () -> completed.set(true),
                errorMessage::set);
        taskRunner.addTasks(taskClass);
        taskRunner.run();
        return new TaskResult(completed, errorMessage);
    }

    private static RawTransactionInput rawInput(long value) {
        Transaction parentTx = new Transaction(PARAMS);
        parentTx.addOutput(Coin.valueOf(value), SegwitAddress.fromKey(PARAMS, new ECKey()));
        Transaction spendingTx = new Transaction(PARAMS);
        TransactionInput input = spendingTx.addInput(parentTx.getOutput(0));
        return new RawTransactionInput(input);
    }

    private static Transaction parentTx(RawTransactionInput input) {
        return new Transaction(PARAMS, input.parentTransaction);
    }

    private static Transaction unsignedSwapTx(RawTransactionInput buyerBsqInput,
                                              RawTransactionInput sellerBtcInput) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(new Transaction(PARAMS, buyerBsqInput.parentTransaction).getOutput(0));
        transaction.addInput(new Transaction(PARAMS, sellerBtcInput.parentTransaction).getOutput(0));
        return transaction;
    }

    private static String addressString() {
        return SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    }

    private record Fixture(BsqSwapTrade trade,
                           BsqSwapProtocolModel protocolModel,
                           BtcWalletService btcWalletService,
                           BsqWalletService bsqWalletService,
                           TradeWalletService tradeWalletService,
                           DaoFacade daoFacade) {
    }

    private record TaskResult(AtomicBoolean completed,
                              AtomicReference<String> errorMessage) {
    }
}

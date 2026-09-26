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

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoCheckpointTestFixture;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapTxInputsMessage;
import bisq.core.trade.protocol.bsq_swap.messages.BuyersBsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.ProcessBuyersBsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.ProcessBsqSwapTxInputsMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessTxInputsMessageTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final String TRADE_ID = "trade-id";
    private static final NodeAddress PEER = new NodeAddress("peer.onion:8000");
    private static final long BTC_TRADE_AMOUNT = 100_000;
    private static final long BSQ_TRADE_AMOUNT = 100_000;
    // Maker and taker pay the same fee, so the buyer's required BSQ input is the same in both seller roles.
    private static final long TRADE_FEE = 10;
    private static final long REQUIRED_BSQ_INPUT = BSQ_TRADE_AMOUNT + TRADE_FEE;
    private static final long TX_FEE_PER_VBYTE = 1;
    private static final String LISTED_TWICE = "Buyers BSQ inputs must not spend the same output twice";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsBuyersBsqInput(boolean sellerAsMaker) {
        Fixture fixture = new Fixture(REQUIRED_BSQ_INPUT);

        TaskResult result = fixture.process(sellerAsMaker, fixture.buyersInputs);

        assertTrue(result.completed.get(), result.errorMessage.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsTwoOutputsOfTheSameParentTx(boolean sellerAsMaker) {
        // For example the payout and the change of one earlier tx of the buyer
        Fixture fixture = new Fixture(REQUIRED_BSQ_INPUT / 2, REQUIRED_BSQ_INPUT / 2);

        TaskResult result = fixture.process(sellerAsMaker, fixture.buyersInputs);

        assertTrue(result.completed.get(), result.errorMessage.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsBuyersBsqInputListedTwice(boolean sellerAsMaker) {
        // The buyer's BSQ output holds half of the required value and is listed twice
        Fixture fixture = new Fixture(REQUIRED_BSQ_INPUT / 2);
        RawTransactionInput buyersInput = fixture.buyersInputs.get(0);

        TaskResult result = fixture.process(sellerAsMaker, List.of(buyersInput, buyersInput));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(LISTED_TWICE));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsBuyersBsqInputListedTwiceInAnotherForm(boolean sellerAsMaker) {
        Fixture fixture = new Fixture(REQUIRED_BSQ_INPUT / 2);
        RawTransactionInput buyersInput = fixture.buyersInputs.get(0);
        // The same output, described with the parent tx serialized without its witness
        RawTransactionInput sameOutput = RawTransactionInput.fromProto(buyersInput.toProtoMessage().toBuilder()
                .setParentTransaction(ByteString.copyFrom(fixture.buyersParent.bitcoinSerialize(false)))
                .build());
        assertNotEquals(buyersInput, sameOutput);

        TaskResult result = fixture.process(sellerAsMaker, List.of(buyersInput, sameOutput));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(LISTED_TWICE));
    }

    private static Transaction parentTx(long... values) {
        Transaction parentTx = new Transaction(PARAMS);
        TransactionInput parentInput = parentTx.addInput(Sha256Hash.of(new ECKey().getPubKey()),
                0,
                ScriptBuilder.createEmpty());
        TransactionWitness parentWitness = new TransactionWitness(2);
        parentWitness.setPush(0, new byte[]{1});
        parentWitness.setPush(1, new byte[]{2});
        parentInput.setWitness(parentWitness);
        for (long value : values) {
            parentTx.addOutput(Coin.valueOf(value), SegwitAddress.fromKey(PARAMS, new ECKey()));
        }
        return new Transaction(PARAMS, parentTx.bitcoinSerialize());
    }

    private static String addressString() {
        return SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    }

    private static class Fixture {
        private final DaoCheckpointTestFixture dao = new DaoCheckpointTestFixture();
        private final Transaction buyersParent;
        private final List<RawTransactionInput> buyersInputs;
        private final BsqSwapProtocolModel protocolModel = new BsqSwapProtocolModel(mock(PubKeyRing.class));
        private final BsqSwapTrade trade = mock(BsqSwapTrade.class);

        // The buyer's BSQ outputs, as the DAO state holds them, all in one parent tx
        Fixture(long... buyersOutputValues) {
            buyersParent = parentTx(buyersOutputValues);
            TxOutputType[] outputTypes = new TxOutputType[buyersOutputValues.length];
            Arrays.fill(outputTypes, TxOutputType.BSQ_OUTPUT);
            dao.addParsedTx(buyersParent, outputTypes);
            buyersInputs = buyersParent.getOutputs().stream()
                    .map(output -> new RawTransactionInput(new Transaction(PARAMS).addInput(output)))
                    .collect(Collectors.toList());

            BtcWalletService btcWalletService = mock(BtcWalletService.class);
            when(btcWalletService.getParams()).thenReturn(PARAMS);
            when(btcWalletService.getTxFromSerializedTx(any()))
                    .thenAnswer(invocation -> new Transaction(PARAMS, invocation.getArgument(0)));
            Provider provider = mock(Provider.class);
            when(provider.getBtcWalletService()).thenReturn(btcWalletService);
            when(provider.getDaoFacade()).thenReturn(dao.facade);
            protocolModel.applyTransient(provider, mock(TradeManager.class), mock(Offer.class));

            when(trade.getBsqSwapProtocolModel()).thenReturn(protocolModel);
            when(trade.getAmountAsLong()).thenReturn(BTC_TRADE_AMOUNT);
            when(trade.getBsqTradeAmount()).thenReturn(BSQ_TRADE_AMOUNT);
            when(trade.getMakerFeeAsLong()).thenReturn(TRADE_FEE);
            when(trade.getTakerFeeAsLong()).thenReturn(TRADE_FEE);
            when(trade.getTxFeePerVbyte()).thenReturn(TX_FEE_PER_VBYTE);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        TaskResult process(boolean sellerAsMaker, List<RawTransactionInput> buyersInputs) {
            protocolModel.setTradeMessage(sellerAsMaker ?
                    new BuyersBsqSwapRequest(TRADE_ID, PEER, mock(PubKeyRing.class), BTC_TRADE_AMOUNT,
                            TX_FEE_PER_VBYTE, TRADE_FEE, TRADE_FEE, 1, buyersInputs, 0,
                            addressString(), addressString()) :
                    new BsqSwapTxInputsMessage(TRADE_ID, PEER, buyersInputs, 0,
                            addressString(), addressString()));

            AtomicBoolean completed = new AtomicBoolean();
            AtomicReference<String> errorMessage = new AtomicReference<>("");
            TaskRunner taskRunner = new TaskRunner(trade,
                    BsqSwapTrade.class,
                    () -> completed.set(true),
                    errorMessage::set);
            taskRunner.addTasks(sellerAsMaker ? ProcessBuyersBsqSwapRequest.class : ProcessBsqSwapTxInputsMessage.class);
            taskRunner.run();
            return new TaskResult(completed, errorMessage);
        }
    }

    private record TaskResult(AtomicBoolean completed,
                              AtomicReference<String> errorMessage) {
    }
}

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
import bisq.core.btc.wallet.WalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_maker.SellerAsMakerProcessBsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.seller_as_taker.SellerAsTakerProcessBsqSwapFinalizedTxMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.Wallet;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the seller's processing of the buyer's finalized tx with real transactions. The buyer's BSQ inputs come first
 * and the buyer signs them, the seller has signed its input after them.
 */
class ProcessBsqSwapFinalizedTxMessageTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final String TRADE_ID = "trade-id";
    private static final NodeAddress PEER = new NodeAddress("peer.onion:8000");
    private static final String NOT_IN_WALLET_FORM =
            "Transaction input 0 is not a P2PK, P2PKH or P2WPKH spend in the form of a Bisq wallet";

    @ParameterizedTest
    @ValueSource(classes = {SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
            SellerAsTakerProcessBsqSwapFinalizedTxMessage.class})
    void acceptsFinalizedTxSignedByTheBuyer(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction finalizedTx = fixture.finalizedTx(fixture.buyersKey);

        TaskResult result = fixture.process(taskClass, finalizedTx);

        assertTrue(result.completed.get(), result.errorMessage.get());
        verify(fixture.trade).setState(BsqSwapTrade.State.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(classes = {SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
            SellerAsTakerProcessBsqSwapFinalizedTxMessage.class})
    void acceptsFinalizedTxWithASignedLegacyBuyerInput(Class<? extends Task<?>> taskClass) {
        // The legacy keychain of an older BSQ wallet
        Fixture fixture = new Fixture(true);
        Transaction finalizedTx = fixture.finalizedTx(fixture.buyersKey);

        TaskResult result = fixture.process(taskClass, finalizedTx);

        assertTrue(result.completed.get(), result.errorMessage.get());
        verify(fixture.trade).setState(BsqSwapTrade.State.COMPLETED);
    }

    // The buyer signs its inputs with BsqWalletService.signBsqSwapTransaction, which uses
    // WalletService.signTransactionInput
    @ParameterizedTest
    @MethodSource("sellerTasksAndBuyersInputTypes")
    void acceptsFinalizedTxSignedByTheBsqWallet(Class<? extends Task<?>> taskClass, boolean legacyBuyersInput) {
        Fixture fixture = new Fixture(legacyBuyersInput);
        Transaction finalizedTx = fixture.finalizedTxSignedByTheBsqWallet();

        TaskResult result = fixture.process(taskClass, finalizedTx);

        assertTrue(result.completed.get(), result.errorMessage.get());
        verify(fixture.trade).setState(BsqSwapTrade.State.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(classes = {SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
            SellerAsTakerProcessBsqSwapFinalizedTxMessage.class})
    void rejectsFinalizedTxWithoutSignatureOfTheBuyer(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        // The tx as we signed it, without any signature of the buyer
        Transaction finalizedTx = new Transaction(PARAMS, fixture.sellersTx.bitcoinSerialize());

        TaskResult result = fixture.process(taskClass, finalizedTx);

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
        verify(fixture.trade, never()).setState(any());
        verify(fixture.openOfferManager, never()).closeOpenOffer(any());
    }

    @ParameterizedTest
    @ValueSource(classes = {SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
            SellerAsTakerProcessBsqSwapFinalizedTxMessage.class})
    void rejectsFinalizedTxSignedWithAnotherKey(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction finalizedTx = fixture.finalizedTx(new ECKey());

        TaskResult result = fixture.process(taskClass, finalizedTx);

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Transaction input 0 has no valid signature"));
        verify(fixture.trade, never()).setState(any());
        verify(fixture.openOfferManager, never()).closeOpenOffer(any());
    }

    @ParameterizedTest
    @ValueSource(classes = {SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
            SellerAsTakerProcessBsqSwapFinalizedTxMessage.class})
    void rejectsFinalizedTxWithASignatureForAnotherTx(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction finalizedTx = fixture.finalizedTx(fixture.buyersKey);
        // A valid signature of the buyer's key, but for a tx with another output
        Transaction otherTx = new Transaction(PARAMS, fixture.sellersTx.bitcoinSerialize());
        otherTx.addOutput(Coin.valueOf(1_000), SegwitAddress.fromKey(PARAMS, new ECKey()));
        TransactionWitness otherWitness = fixture.signBuyersInput(otherTx, fixture.buyersKey);
        finalizedTx.getInput(0).setWitness(otherWitness);

        TaskResult result = fixture.process(taskClass, finalizedTx);

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Transaction input 0 has no valid signature"));
        verify(fixture.trade, never()).setState(any());
    }

    @ParameterizedTest
    @ValueSource(classes = {SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
            SellerAsTakerProcessBsqSwapFinalizedTxMessage.class})
    void rejectsFinalizedTxWhichSpendsABuyerOutpointTwice(Class<? extends Task<?>> taskClass) {
        // As if we had accepted the buyer's input twice when we built the tx. The buyer signs both copies.
        Fixture fixture = new Fixture(false, 2);
        Transaction finalizedTx = fixture.finalizedTx(fixture.buyersKey);

        TaskResult result = fixture.process(taskClass, finalizedTx);

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Invalid transaction"));
        verify(fixture.trade, never()).setState(any());
        verify(fixture.openOfferManager, never()).closeOpenOffer(any());
    }

    @ParameterizedTest
    @ValueSource(classes = {SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
            SellerAsTakerProcessBsqSwapFinalizedTxMessage.class})
    void completesWithoutProcessingAgainWhenWeHaveTheTxAlready(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        when(fixture.trade.getTransaction(any())).thenReturn(fixture.finalizedTx(fixture.buyersKey));
        Transaction resentTx = new Transaction(PARAMS, fixture.sellersTx.bitcoinSerialize());

        TaskResult result = fixture.process(taskClass, resentTx);

        assertTrue(result.completed.get(), result.errorMessage.get());
        verify(fixture.trade, never()).applyTransaction(any());
    }

    private static Transaction parentTx(ECKey key, long value) {
        return parentTx(ScriptBuilder.createP2WPKHOutputScript(key), value);
    }

    private static Transaction parentTx(Script outputScript, long value) {
        Transaction parentTx = new Transaction(PARAMS);
        TransactionInput parentInput = parentTx.addInput(Sha256Hash.of(new ECKey().getPubKey()),
                0,
                ScriptBuilder.createEmpty());
        TransactionWitness parentWitness = new TransactionWitness(2);
        parentWitness.setPush(0, new byte[]{1});
        parentWitness.setPush(1, new byte[]{2});
        parentInput.setWitness(parentWitness);
        parentTx.addOutput(Coin.valueOf(value), outputScript);
        return new Transaction(PARAMS, parentTx.bitcoinSerialize());
    }

    private static Stream<Arguments> sellerTasksAndBuyersInputTypes() {
        return Stream.of(SellerAsMakerProcessBsqSwapFinalizedTxMessage.class,
                        SellerAsTakerProcessBsqSwapFinalizedTxMessage.class)
                .flatMap(taskClass -> Stream.of(false, true)
                        .map(legacyBuyersInput -> Arguments.of(taskClass, legacyBuyersInput)));
    }

    private static TransactionWitness p2wpkhWitness(Transaction tx, int index, ECKey key, TransactionOutput spent) {
        TransactionSignature signature = tx.calculateWitnessSignature(index,
                key,
                ScriptBuilder.createP2PKHOutputScript(key),
                spent.getValue(),
                Transaction.SigHash.ALL,
                false);
        return TransactionWitness.redeemP2WPKH(signature, key);
    }

    private static class Fixture {
        private final ECKey buyersKey = new ECKey();
        private final ECKey sellersKey = new ECKey();
        private final Transaction buyersParent;
        private final int numBuyersInputs;
        private final Transaction sellersParent = parentTx(sellersKey, 150_000);
        private final Transaction sellersTx;
        private final BsqSwapProtocolModel protocolModel = new BsqSwapProtocolModel(mock(PubKeyRing.class));
        private final BsqSwapTrade trade = mock(BsqSwapTrade.class);
        private final OpenOfferManager openOfferManager = mock(OpenOfferManager.class);

        Fixture() {
            this(false);
        }

        Fixture(boolean legacyBuyersInput) {
            this(legacyBuyersInput, 1);
        }

        // With numBuyersInputs above 1 the buyer's input was accepted that many times.
        Fixture(boolean legacyBuyersInput, int numBuyersInputs) {
            this.numBuyersInputs = numBuyersInputs;
            buyersParent = legacyBuyersInput ?
                    parentTx(ScriptBuilder.createP2PKHOutputScript(buyersKey), 100_010) :
                    parentTx(buyersKey, 100_010);
            // The tx as we built and signed it in SellerCreatesAndSignsTx: buyer inputs first, then our input.
            sellersTx = new Transaction(PARAMS);
            for (int i = 0; i < numBuyersInputs; i++) {
                sellersTx.addInput(buyersParent.getOutput(0));
            }
            sellersTx.addInput(sellersParent.getOutput(0));
            sellersTx.addOutput(Coin.valueOf(99_990), SegwitAddress.fromKey(PARAMS, new ECKey()));
            sellersTx.addOutput(Coin.valueOf(98_970), SegwitAddress.fromKey(PARAMS, new ECKey()));
            sellersTx.addOutput(Coin.valueOf(48_660), SegwitAddress.fromKey(PARAMS, new ECKey()));
            sellersTx.getInput(numBuyersInputs).setWitness(p2wpkhWitness(sellersTx,
                    numBuyersInputs,
                    sellersKey,
                    sellersParent.getOutput(0)));

            BtcWalletService btcWalletService = mock(BtcWalletService.class);
            when(btcWalletService.getParams()).thenReturn(PARAMS);
            when(btcWalletService.getTxFromSerializedTx(any()))
                    .thenAnswer(invocation -> new Transaction(PARAMS, invocation.getArgument(0)));
            Provider provider = mock(Provider.class);
            when(provider.getBtcWalletService()).thenReturn(btcWalletService);
            when(provider.getOpenOfferManager()).thenReturn(openOfferManager);
            Offer offer = mock(Offer.class);
            when(offer.getId()).thenReturn(TRADE_ID);
            protocolModel.applyTransient(provider, mock(TradeManager.class), offer);
            RawTransactionInput buyersInput = new RawTransactionInput(
                    new Transaction(PARAMS).addInput(buyersParent.getOutput(0)));
            protocolModel.getTradePeer().setInputs(Collections.nCopies(numBuyersInputs, buyersInput));
            protocolModel.setTx(sellersTx.bitcoinSerialize());

            when(trade.getBsqSwapProtocolModel()).thenReturn(protocolModel);
            when(trade.getOffer()).thenReturn(offer);
        }

        TransactionWitness signBuyersInput(Transaction tx, ECKey key) {
            return p2wpkhWitness(tx, 0, key, buyersParent.getOutput(0));
        }

        // The buyer adds its signatures to the tx which we signed.
        Transaction finalizedTx(ECKey key) {
            Transaction finalizedTx = new Transaction(PARAMS, sellersTx.bitcoinSerialize());
            TransactionOutput buyersOutput = buyersParent.getOutput(0);
            for (int i = 0; i < numBuyersInputs; i++) {
                if (ScriptPattern.isP2PKH(buyersOutput.getScriptPubKey())) {
                    TransactionSignature signature = finalizedTx.calculateSignature(i,
                            key,
                            buyersOutput.getScriptPubKey(),
                            Transaction.SigHash.ALL,
                            false);
                    finalizedTx.getInput(i).setScriptSig(ScriptBuilder.createInputScript(signature, key));
                } else {
                    finalizedTx.getInput(i).setWitness(p2wpkhWitness(finalizedTx, i, key, buyersOutput));
                }
            }
            return finalizedTx;
        }

        // The buyer builds the tx from its wallet outputs and signs its inputs as
        // BsqWalletService.signBsqSwapTransaction does.
        Transaction finalizedTxSignedByTheBsqWallet() {
            // Wallet.createBasic needs a context of PARAMS on the thread, and another test may leave a RegTest one
            Context.propagate(new Context(PARAMS));
            Wallet wallet = Wallet.createBasic(PARAMS);
            wallet.importKey(buyersKey);
            Transaction finalizedTx = new Transaction(PARAMS);
            for (int i = 0; i < numBuyersInputs; i++) {
                finalizedTx.addInput(buyersParent.getOutput(0));
            }
            finalizedTx.addInput(sellersParent.getOutput(0))
                    .setWitness(sellersTx.getInput(numBuyersInputs).getWitness());
            sellersTx.getOutputs().forEach(output ->
                    finalizedTx.addOutput(output.getValue(), output.getScriptPubKey()));
            for (int i = 0; i < numBuyersInputs; i++) {
                WalletService.signTransactionInput(wallet, null, finalizedTx, finalizedTx.getInput(i), i);
            }
            return finalizedTx;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        TaskResult process(Class<? extends Task<?>> taskClass, Transaction finalizedTx) {
            BsqSwapFinalizedTxMessage message = new BsqSwapFinalizedTxMessage(TRADE_ID,
                    PEER,
                    finalizedTx.bitcoinSerialize());
            // As received over the network
            protocolModel.setTradeMessage(BsqSwapFinalizedTxMessage.fromProto(
                    message.toProtoNetworkEnvelope().getBsqSwapFinalizedTxMessage(),
                    Version.getP2PMessageVersion()));

            AtomicBoolean completed = new AtomicBoolean();
            AtomicReference<String> errorMessage = new AtomicReference<>("");
            TaskRunner taskRunner = new TaskRunner(trade,
                    BsqSwapTrade.class,
                    () -> completed.set(true),
                    errorMessage::set);
            taskRunner.addTasks(taskClass);
            taskRunner.run();
            return new TaskResult(completed, errorMessage);
        }
    }

    private record TaskResult(AtomicBoolean completed,
                              AtomicReference<String> errorMessage) {
    }
}

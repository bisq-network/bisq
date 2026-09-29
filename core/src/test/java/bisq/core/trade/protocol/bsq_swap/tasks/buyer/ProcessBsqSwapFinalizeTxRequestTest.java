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

package bisq.core.trade.protocol.bsq_swap.tasks.buyer;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoCheckpointTestFixture;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_maker.BuyerAsMakerProcessBsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_taker.BuyerAsTakerProcessBsqSwapFinalizeTxRequest;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptPattern;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the buyer's processing of the seller's finalize request with real transactions, as they would arrive from the
 * seller. The seller's parent tx has a smaller output 0 and a larger output 1.
 */
class ProcessBsqSwapFinalizeTxRequestTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final String TRADE_ID = "trade-id";
    private static final NodeAddress PEER = new NodeAddress("peer.onion:8000");
    private static final long BTC_TRADE_AMOUNT = 100_000;
    private static final long BSQ_TRADE_AMOUNT = 100_000;
    // Maker and taker fee are equal, so the numbers below are the same for buyer as maker and buyer as taker.
    private static final long TRADE_FEE = 10;
    private static final long TX_FEE_PER_VBYTE = 10;
    private static final int CHAIN_HEIGHT = 1;

    // Buyer: one segwit BSQ input and no BSQ change is 104 vbytes, so the buyer's tx fee is 10 * 104 - 10 = 1030.
    private static final long BUYERS_BSQ_INPUT = BSQ_TRADE_AMOUNT + TRADE_FEE;
    private static final long BUYERS_BTC_PAYOUT = BTC_TRADE_AMOUNT - 1030;
    // Seller: one segwit BTC input with BTC change is 135 vbytes, so the seller's tx fee is 10 * 135 - 10 = 1340.
    private static final long SELLERS_BSQ_PAYOUT = BSQ_TRADE_AMOUNT - TRADE_FEE;
    private static final long LARGER_OUTPUT = 150_000;
    private static final long SELLERS_CHANGE = LARGER_OUTPUT - BUYERS_BTC_PAYOUT - 1340 - 1030;
    // Spending this output while describing the larger one would leave a miner fee of only 190 instead of 2390.
    private static final long SMALLER_OUTPUT = 147_800;
    // A legacy BTC input is 149 vbytes, so the seller's tx fee is 10 * (5 + 149 + 62) - 10 = 2150.
    private static final long SELLERS_CHANGE_WITH_LEGACY_INPUT = LARGER_OUTPUT - BUYERS_BTC_PAYOUT - 2150 - 1030;

    private static final String NOT_IN_WALLET_FORM =
            "Transaction input 1 is not a P2PK, P2PKH or P2WPKH spend in the form of a Bisq wallet";

    private enum SellersSignature {VALID, NONE, OTHER_KEY, OTHER_VALUE}

    private enum SellersOutputType {
        P2WPKH(ScriptBuilder::createP2WPKHOutputScript),
        P2PKH(ScriptBuilder::createP2PKHOutputScript),
        P2PK(ScriptBuilder::createP2PKOutputScript);

        private final Function<ECKey, Script> script;

        SellersOutputType(Function<ECKey, Script> script) {
            this.script = script;
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void acceptsSellerInputWhichSpendsTheDescribedOutput(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertTrue(result.completed.get(), result.errorMessage.get());
        assertEquals(List.of(fixture.describedSellersInput()), fixture.protocolModel.getTradePeer().getInputs());
        assertEquals(SELLERS_CHANGE, fixture.protocolModel.getTradePeer().getChange());
    }

    // For a legacy input the seller's signature does not commit to the spent value, so only the binding to the
    // outpoint rejects the other output.
    @ParameterizedTest
    @MethodSource("buyerTasksAndSellersOutputTypes")
    void rejectsSellerInputWhichSpendsAnotherOutputOfTheDescribedParent(Class<? extends Task<?>> taskClass,
                                                                       SellersOutputType sellersOutputType) {
        Fixture fixture = new Fixture(sellersOutputType.script);
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(0));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Transaction input 1 does not match expected seller input 0"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerInputWhichSpendsAnOutputOfAnotherParent(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction otherParent = parentTx(fixture.sellersKey, SMALLER_OUTPUT, LARGER_OUTPUT);
        Transaction sellersTx = fixture.sellersTx(otherParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Transaction input 1 does not match expected seller input 0"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerInputWhichIsDescribedAndSpentTwice(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        TransactionOutput describedOutput = fixture.sellersParent.getOutput(1);
        Transaction sellersTx = fixture.sellersTx(describedOutput, describedOutput);

        TaskResult result = fixture.process(taskClass,
                sellersTx,
                List.of(fixture.describedSellersInput(), fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Invalid transaction"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerInputWithoutDescription(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1), fixture.sellersParent.getOutput(0));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Number of sellersBtcInputs in tx must match"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerChangeAboveTheValueLeftAfterPayoutAndFees(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass,
                sellersTx,
                List.of(fixture.describedSellersInput()),
                SELLERS_CHANGE + 1);

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Change must be smaller or equal to expectedChange"));
    }

    @ParameterizedTest
    @MethodSource("buyerTasksAndBsqOutputTypes")
    void rejectsSellerInputWhichSpendsAnUnspentBsqOutput(Class<? extends Task<?>> taskClass,
                                                         TxOutputType bsqOutputType) {
        Fixture fixture = new Fixture();
        fixture.addSellersParentToDaoState(TxOutputType.BTC_OUTPUT, bsqOutputType);
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("spends a BSQ output"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void acceptsSellerInputWhichSpendsABtcOutputOfABsqTx(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        // E.g. the BTC change of a tx which paid a trade fee in BSQ
        fixture.addSellersParentToDaoState(TxOutputType.BSQ_OUTPUT, TxOutputType.BTC_OUTPUT);
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertTrue(result.completed.get(), result.errorMessage.get());
        // The DAO state knows the output, so checking only for a known output would reject an honest seller.
        assertTrue(fixture.dao.daoStateService.existsTxOutput(
                new TxOutputKey(fixture.sellersParent.getTxId().toString(), 1)));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerInputWhichSpendsABsqOutputAndDescribesABtcOutputOfTheSameTx(
            Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        fixture.addSellersParentToDaoState(TxOutputType.UNLOCK_OUTPUT, TxOutputType.BTC_OUTPUT);
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(0));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Transaction input 1 does not match expected seller input 0"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerInputsWhenDaoStateIsNotReadyAndInSync(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        fixture.dao.failCheckpoint();
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("DAO state is not ready and in sync"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerInputWithoutSignature(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(SellersSignature.NONE, fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerInputSignedWithAnotherKey(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(SellersSignature.OTHER_KEY, fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Transaction input 1 has no valid signature"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSellerInputSignedForAnotherValue(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(SellersSignature.OTHER_VALUE, fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString("Transaction input 1 has no valid signature"));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void acceptsSignedSellerInputWhichSpendsALegacyOutput(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture(ScriptBuilder::createP2PKHOutputScript);
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertTrue(result.completed.get(), result.errorMessage.get());
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsUnsignedSellerInputWhichSpendsALegacyOutput(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture(ScriptBuilder::createP2PKHOutputScript);
        Transaction sellersTx = fixture.sellersTx(SellersSignature.NONE, fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsUnsignedSellerInputWhichSpendsAP2wshOutput(Class<? extends Task<?>> taskClass) {
        // The legacy script interpreter leaves the 32 byte witness program on the stack, which counts as true.
        Fixture fixture = new Fixture(key -> ScriptBuilder.createP2WSHOutputScript(
                ScriptBuilder.createP2PKHOutputScript(key)));
        Transaction sellersTx = fixture.sellersTx(SellersSignature.NONE, fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsUnsignedSellerInputWhichSpendsAP2shWrappedP2wpkhOutput(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture(key -> ScriptBuilder.createP2SHOutputScript(
                ScriptBuilder.createP2WPKHOutputScript(key)));
        Transaction sellersTx = fixture.sellersTx(SellersSignature.NONE, fixture.sellersParent.getOutput(1));
        // Only the redeem script, which the P2SH evaluation accepts without any signature
        sellersTx.getInput(1).setScriptSig(new ScriptBuilder()
                .data(ScriptBuilder.createP2WPKHOutputScript(fixture.sellersKey).getProgram())
                .build());

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSignedP2wpkhSellerInputWithAScriptSig(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));
        sellersTx.getInput(1).setScriptSig(new ScriptBuilder().data(new byte[]{1, 2}).build());

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSignedP2wpkhSellerInputWithAThirdWitnessItem(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));
        TransactionWitness validWitness = sellersTx.getInput(1).getWitness();
        TransactionWitness witness = new TransactionWitness(3);
        witness.setPush(0, validWitness.getPush(0));
        witness.setPush(1, validWitness.getPush(1));
        witness.setPush(2, new byte[]{1});
        sellersTx.getInput(1).setWitness(witness);

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSignedLegacySellerInputWithAWitness(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture(ScriptBuilder::createP2PKHOutputScript);
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{1});
        sellersTx.getInput(1).setWitness(witness);

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsP2wpkhSellerSignatureWithAnyoneCanPayFlag(Class<? extends Task<?>> taskClass) {
        // The script interpreter ignores the ANYONECANPAY flag of a witness signature, the network does not.
        Fixture fixture = new Fixture();
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));
        TransactionWitness witness = sellersTx.getInput(1).getWitness();
        byte[] signature = witness.getPush(0).clone();
        signature[signature.length - 1] = (byte) 0x81;
        TransactionWitness changedWitness = new TransactionWitness(2);
        changedWitness.setPush(0, signature);
        changedWitness.setPush(1, witness.getPush(1));
        sellersTx.getInput(1).setWitness(changedWitness);

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsP2wpkhSellerInputWithAnUncompressedKey(Class<? extends Task<?>> taskClass) {
        ECKey uncompressedKey = ECKey.fromPrivate(new ECKey().getPrivKey(), false);
        Fixture fixture = new Fixture(uncompressedKey,
                key -> ScriptBuilder.createP2WPKHOutputScript(key.getPubKeyHash()));
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSignedLegacySellerInputWithAnExtraPush(Class<? extends Task<?>> taskClass) {
        // Valid for the script interpreter, but the network does not relay it and the seller could remove it later.
        Fixture fixture = new Fixture(ScriptBuilder::createP2PKHOutputScript);
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));
        List<ScriptChunk> chunks = sellersTx.getInput(1).getScriptSig().getChunks();
        sellersTx.getInput(1).setScriptSig(new ScriptBuilder()
                .data(new byte[]{1, 2})
                .data(chunks.get(0).data)
                .data(chunks.get(1).data)
                .build());

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSignedLegacySellerInputWithAHybridEncodedKey(Class<? extends Task<?>> taskClass) {
        // Valid for the script interpreter, but the network does not relay it and the seller cannot change the key.
        ECKey key = new ECKey();
        byte[] hybridPubKey = hybridEncoding(key);
        Fixture fixture = new Fixture(key, ignored -> ScriptBuilder.createP2PKHOutputScript(
                Utils.sha256hash160(hybridPubKey)));
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));
        byte[] signature = sellersTx.getInput(1).getScriptSig().getChunks().get(0).data;
        sellersTx.getInput(1).setScriptSig(new ScriptBuilder().data(signature).data(hybridPubKey).build());

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void rejectsSignedSellerInputWhichSpendsAP2pkOutputWithAHybridEncodedKey(Class<? extends Task<?>> taskClass) {
        ECKey key = new ECKey();
        Fixture fixture = new Fixture(key, ignored -> ScriptBuilder.createP2PKOutputScript(hybridEncoding(key)));
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertFalse(result.completed.get());
        assertThat(result.errorMessage.get(), containsString(NOT_IN_WALLET_FORM));
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void acceptsSignedSellerInputWhichSpendsAP2pkOutput(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture(ScriptBuilder::createP2PKOutputScript);
        Transaction sellersTx = fixture.sellersTx(fixture.sellersParent.getOutput(1));

        TaskResult result = fixture.process(taskClass, sellersTx, List.of(fixture.describedSellersInput()));

        assertTrue(result.completed.get(), result.errorMessage.get());
    }

    @ParameterizedTest
    @ValueSource(classes = {BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
            BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class})
    void acceptsSignedSellerInputsOfDifferentTypes(Class<? extends Task<?>> taskClass) {
        Fixture fixture = new Fixture();
        Transaction legacyParent = parentTx(ScriptBuilder.createP2PKHOutputScript(fixture.sellersKey), LARGER_OUTPUT);
        // One segwit and one legacy input with change are 5 + 68 + 149 + 62 = 284 vbytes, so the tx fee is 2830.
        long change = 2 * LARGER_OUTPUT - BUYERS_BTC_PAYOUT - 2830 - 1030;
        Transaction sellersTx = fixture.sellersTx(SellersSignature.VALID,
                change,
                fixture.sellersParent.getOutput(1),
                legacyParent.getOutput(0));

        TaskResult result = fixture.process(taskClass,
                sellersTx,
                List.of(fixture.describedSellersInput(), rawInput(legacyParent.getOutput(0))),
                change);

        assertTrue(result.completed.get(), result.errorMessage.get());
    }

    private static Stream<Arguments> buyerTasksAndSellersOutputTypes() {
        return Stream.of(BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
                        BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class)
                .flatMap(taskClass -> Stream.of(SellersOutputType.values())
                        .map(sellersOutputType -> Arguments.of(taskClass, sellersOutputType)));
    }

    private static Stream<Arguments> buyerTasksAndBsqOutputTypes() {
        return Stream.of(BuyerAsMakerProcessBsqSwapFinalizeTxRequest.class,
                        BuyerAsTakerProcessBsqSwapFinalizeTxRequest.class)
                .flatMap(taskClass -> Stream.of(TxOutputType.BSQ_OUTPUT,
                                TxOutputType.LOCKUP_OUTPUT,
                                TxOutputType.UNLOCK_OUTPUT)
                        .map(bsqOutputType -> Arguments.of(taskClass, bsqOutputType)));
    }

    private static Transaction parentTx(ECKey key, long... outputValues) {
        return parentTx(ScriptBuilder.createP2WPKHOutputScript(key), outputValues);
    }

    // The parent spends a segwit input, so a segwit description carries it with witness data.
    private static Transaction parentTx(Script outputScript, long... outputValues) {
        Transaction parentTx = new Transaction(PARAMS);
        TransactionInput parentInput = parentTx.addInput(Sha256Hash.of(new ECKey().getPubKey()),
                0,
                ScriptBuilder.createEmpty());
        TransactionWitness parentWitness = new TransactionWitness(2);
        parentWitness.setPush(0, new byte[]{1});
        parentWitness.setPush(1, new byte[]{2});
        parentInput.setWitness(parentWitness);
        for (long outputValue : outputValues) {
            parentTx.addOutput(Coin.valueOf(outputValue), outputScript);
        }
        return new Transaction(PARAMS, parentTx.bitcoinSerialize());
    }

    // The uncompressed encoding with the prefix 0x06 or 0x07, which also tells whether y is even or odd
    private static byte[] hybridEncoding(ECKey key) {
        byte[] pubKey = ECKey.fromPrivate(key.getPrivKey(), false).getPubKey();
        pubKey[0] = (byte) ((pubKey[64] & 1) == 0 ? 0x06 : 0x07);
        return pubKey;
    }

    private static RawTransactionInput rawInput(TransactionOutput output) {
        return new RawTransactionInput(new Transaction(PARAMS).addInput(output));
    }

    private static String address() {
        return SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    }

    private static class Fixture {
        private final ECKey sellersKey;
        private final Transaction buyersParent = parentTx(new ECKey(), BUYERS_BSQ_INPUT);
        private final Transaction sellersParent;
        private final long sellersChange;
        private final String sellersBsqPayoutAddress = address();
        private final String sellersBtcChangeAddress = address();
        private final BsqSwapProtocolModel protocolModel = new BsqSwapProtocolModel(mock(PubKeyRing.class));
        private final BsqSwapTrade trade = mock(BsqSwapTrade.class);
        private final DaoCheckpointTestFixture dao = new DaoCheckpointTestFixture();

        Fixture() {
            this(ScriptBuilder::createP2WPKHOutputScript);
        }

        Fixture(Function<ECKey, Script> sellersOutputScript) {
            this(new ECKey(), sellersOutputScript);
        }

        Fixture(ECKey sellersKey, Function<ECKey, Script> sellersOutputScript) {
            this.sellersKey = sellersKey;
            sellersParent = parentTx(sellersOutputScript.apply(sellersKey), SMALLER_OUTPUT, LARGER_OUTPUT);
            sellersChange = describedSellersInput().isSegwit() ? SELLERS_CHANGE : SELLERS_CHANGE_WITH_LEGACY_INPUT;
            configureFeeService();

            BtcWalletService btcWalletService = mock(BtcWalletService.class);
            when(btcWalletService.getParams()).thenReturn(PARAMS);
            when(btcWalletService.getTxFromSerializedTx(any()))
                    .thenAnswer(invocation -> new Transaction(PARAMS, invocation.getArgument(0)));
            Provider provider = mock(Provider.class);
            when(provider.getBtcWalletService()).thenReturn(btcWalletService);
            when(provider.getDaoFacade()).thenReturn(dao.facade);
            Offer offer = mock(Offer.class);
            when(offer.getId()).thenReturn(TRADE_ID);
            protocolModel.applyTransient(provider, mock(TradeManager.class), offer);

            // State after BuyerCreatesBsqInputsAndChange
            protocolModel.setInputs(List.of(rawInput(buyersParent.getOutput(0))));
            protocolModel.setChange(0);
            protocolModel.setPayout(BUYERS_BTC_PAYOUT);

            when(trade.getBsqSwapProtocolModel()).thenReturn(protocolModel);
            when(trade.getAmountAsLong()).thenReturn(BTC_TRADE_AMOUNT);
            when(trade.getBsqTradeAmount()).thenReturn(BSQ_TRADE_AMOUNT);
            when(trade.getMakerFeeAsLong()).thenReturn(TRADE_FEE);
            when(trade.getTakerFeeAsLong()).thenReturn(TRADE_FEE);
            when(trade.getTxFeePerVbyte()).thenReturn(TX_FEE_PER_VBYTE);
        }

        RawTransactionInput describedSellersInput() {
            return rawInput(sellersParent.getOutput(1));
        }

        // Adds the seller's parent tx to the DAO state as a parsed BSQ tx with the given output types.
        void addSellersParentToDaoState(TxOutputType... outputTypes) {
            dao.addParsedTx(sellersParent, outputTypes);
        }

        Transaction sellersTx(TransactionOutput... spentOutputs) {
            return sellersTx(SellersSignature.VALID, spentOutputs);
        }

        // Builds and signs the tx the way the seller does: buyer inputs first, then the seller's inputs, and the
        // outputs in the order of TradeWalletService.buildBsqSwapTx. The buyer's input is not signed yet.
        Transaction sellersTx(SellersSignature sellersSignature, TransactionOutput... spentOutputs) {
            return sellersTx(sellersSignature, sellersChange, spentOutputs);
        }

        Transaction sellersTx(SellersSignature sellersSignature, long change, TransactionOutput... spentOutputs) {
            Transaction tx = new Transaction(PARAMS);
            tx.addInput(buyersParent.getOutput(0));
            for (TransactionOutput spentOutput : spentOutputs) {
                tx.addInput(spentOutput);
            }
            tx.addOutput(Coin.valueOf(SELLERS_BSQ_PAYOUT), Address.fromString(PARAMS, sellersBsqPayoutAddress));
            tx.addOutput(Coin.valueOf(BUYERS_BTC_PAYOUT), SegwitAddress.fromKey(PARAMS, new ECKey()));
            tx.addOutput(Coin.valueOf(change), Address.fromString(PARAMS, sellersBtcChangeAddress));
            if (sellersSignature == SellersSignature.NONE) {
                return tx;
            }
            ECKey key = sellersSignature == SellersSignature.OTHER_KEY ? new ECKey() : sellersKey;
            for (int i = 0; i < spentOutputs.length; i++) {
                int inputIndex = 1 + i;
                TransactionOutput spentOutput = spentOutputs[i];
                TransactionInput input = tx.getInput(inputIndex);
                if (ScriptPattern.isP2WPKH(spentOutput.getScriptPubKey())) {
                    Coin value = sellersSignature == SellersSignature.OTHER_VALUE ?
                            spentOutput.getValue().add(Coin.SATOSHI) :
                            spentOutput.getValue();
                    TransactionSignature signature = tx.calculateWitnessSignature(inputIndex,
                            key,
                            ScriptBuilder.createP2PKHOutputScript(key.getPubKeyHash()),
                            value,
                            Transaction.SigHash.ALL,
                            false);
                    // Built by hand, as TransactionWitness.redeemP2WPKH accepts only compressed keys
                    TransactionWitness witness = new TransactionWitness(2);
                    witness.setPush(0, signature.encodeToBitcoin());
                    witness.setPush(1, key.getPubKey());
                    input.setWitness(witness);
                } else {
                    TransactionSignature signature = tx.calculateSignature(inputIndex,
                            key,
                            spentOutput.getScriptPubKey(),
                            Transaction.SigHash.ALL,
                            false);
                    input.setScriptSig(ScriptPattern.isP2PK(spentOutput.getScriptPubKey()) ?
                            ScriptBuilder.createInputScript(signature) :
                            ScriptBuilder.createInputScript(signature, key));
                }
            }
            return tx;
        }

        TaskResult process(Class<? extends Task<?>> taskClass,
                           Transaction sellersTx,
                           List<RawTransactionInput> describedSellersInputs) {
            return process(taskClass, sellersTx, describedSellersInputs, sellersChange);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        TaskResult process(Class<? extends Task<?>> taskClass,
                           Transaction sellersTx,
                           List<RawTransactionInput> describedSellersInputs,
                           long requestedSellersChange) {
            BsqSwapFinalizeTxRequest request = new BsqSwapFinalizeTxRequest(TRADE_ID,
                    PEER,
                    sellersTx.bitcoinSerialize(),
                    describedSellersInputs,
                    requestedSellersChange,
                    sellersBsqPayoutAddress,
                    sellersBtcChangeAddress);
            // As received over the network
            protocolModel.setTradeMessage(BsqSwapFinalizeTxRequest.fromProto(
                    request.toProtoNetworkEnvelope().getBsqSwapFinalizeTxRequest(),
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

    private static void configureFeeService() {
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        FilterManager filterManager = mock(FilterManager.class);
        when(periodService.getChainHeight()).thenReturn(CHAIN_HEIGHT);
        when(filterManager.getFilter()).thenReturn(null);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BSQ, CHAIN_HEIGHT))
                .thenReturn(Coin.valueOf(TRADE_FEE));
        when(daoStateService.getParamValueAsCoin(Param.MIN_MAKER_FEE_BSQ, CHAIN_HEIGHT))
                .thenReturn(Coin.valueOf(TRADE_FEE));
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BSQ, CHAIN_HEIGHT))
                .thenReturn(Coin.valueOf(TRADE_FEE));
        when(daoStateService.getParamValueAsCoin(Param.MIN_TAKER_FEE_BSQ, CHAIN_HEIGHT))
                .thenReturn(Coin.valueOf(TRADE_FEE));

        new FeeService(daoStateService, periodService).onAllServicesInitialized(filterManager);
    }

    private record TaskResult(AtomicBoolean completed,
                              AtomicReference<String> errorMessage) {
    }
}

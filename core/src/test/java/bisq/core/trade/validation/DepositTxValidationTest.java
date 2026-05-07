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

package bisq.core.trade.validation;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.DepositTxValidation.checkDepositTxMatchesIgnoringWitnessesAndScriptSigs;
import static bisq.core.trade.validation.ValidationTestUtils.PARAMS;
import static bisq.core.trade.validation.ValidationTestUtils.btcWalletService;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepositTxValidationTest {
    static final String SELLER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    static final String BUYER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();

    private static final String MAKER_ROLE = "Maker";
    private static final String TAKER_ROLE = "Taker";

    /* --------------------------------------------------------------------- */
    // Deposit tx comparison
    /* --------------------------------------------------------------------- */

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsAcceptsMatchingTxsAndReturnsDepositTx() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = copy(expectedDepositTx);
        addSignatureData(expectedDepositTx, new byte[]{1, 2}, new byte[]{3});
        addSignatureData(depositTx, new byte[]{4, 5}, new byte[]{6});

        assertSame(depositTx,
                checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentInputOutpoint() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 1);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentOutputAmount() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(9_999), BUYER_ADDRESS, 0);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentOutputAddress() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(10_000), SELLER_ADDRESS, 0);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsNullPublicArgs() {
        Transaction depositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        BtcWalletService btcWalletService = ValidationTestUtils.btcWalletService();

        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(null,
                        depositTx,
                        btcWalletService));
        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        null,
                        btcWalletService));
        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        depositTx,
                        (BtcWalletService) null));
    }

    /* --------------------------------------------------------------------- */
    // Unsigned transaction
    /* --------------------------------------------------------------------- */

    @Test
    void checkTransactionIsUnsignedAcceptsValidUnsignedTransaction() {
        byte[] depositTxWithoutWitnesses = ValidationTestUtils.serializedTransaction();

        assertSame(depositTxWithoutWitnesses,
                DepositTxValidation.checkTransactionIsUnsigned(depositTxWithoutWitnesses,
                        btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsMalformedSerializedTransaction() {
        assertThrows(RuntimeException.class, () -> DepositTxValidation.checkTransactionIsUnsigned(
                new byte[]{1, 2, 3},
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsStructurallyInvalidTransaction() {
        assertThrows(IllegalArgumentException.class, () -> DepositTxValidation.checkTransactionIsUnsigned(
                ValidationTestUtils.serializedTransactionWithoutOutputs(),
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsTransactionWithScriptSig() {
        assertThrows(IllegalArgumentException.class, () -> DepositTxValidation.checkTransactionIsUnsigned(
                ValidationTestUtils.serializedTransactionWithScriptSig(),
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsTransactionWithWitness() {
        assertThrows(IllegalArgumentException.class, () -> DepositTxValidation.checkTransactionIsUnsigned(
                ValidationTestUtils.serializedTransactionWithWitness(),
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsNullTransaction() {
        assertThrows(NullPointerException.class, () -> DepositTxValidation.checkTransactionIsUnsigned(null,
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> DepositTxValidation.checkTransactionIsUnsigned(
                ValidationTestUtils.serializedTransaction(),
                null));
    }

    /* --------------------------------------------------------------------- */
    // Maker and taker inputs
    /* --------------------------------------------------------------------- */

    @Test
    void checkTakersRawTransactionInputsAcceptsSellerInputsForBuyOffer() {
        Coin tradeAmount = Coin.valueOf(20_000);
        Coin tradeTxFee = Coin.valueOf(300);
        Offer offer = ValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = ValidationTestUtils.trade(offer, tradeTxFee);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = ValidationTestUtils.rawTransactionInputs(btcWalletService,
                offer.getSellerSecurityDeposit()
                        .add(tradeAmount)
                        .add(tradeTxFee.multiply(2)));

        assertSame(rawTransactionInputs, DepositTxValidation.checkTakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                trade.getOffer(),
                trade.getTradeTxFee(),
                tradeAmount));
    }

    @Test
    void checkTakersRawTransactionInputsAcceptsBuyerInputsForSellOffer() {
        Coin tradeAmount = Coin.valueOf(20_000);
        Coin tradeTxFee = Coin.valueOf(300);
        Offer offer = ValidationTestUtils.offer(false, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = ValidationTestUtils.trade(offer, tradeTxFee);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = ValidationTestUtils.rawTransactionInputs(btcWalletService,
                offer.getBuyerSecurityDeposit().add(tradeTxFee.multiply(2)));

        assertSame(rawTransactionInputs, DepositTxValidation.checkTakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                trade.getOffer(),
                trade.getTradeTxFee(),
                tradeAmount));
    }

    @Test
    void checkMakersRawTransactionInputsAcceptsBuyerInputsForBuyOffer() {
        Offer offer = ValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = ValidationTestUtils.rawTransactionInputs(btcWalletService,
                offer.getBuyerSecurityDeposit());

        assertSame(rawTransactionInputs, DepositTxValidation.checkMakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                offer));
    }

    @Test
    void checkMakersRawTransactionInputsAcceptsSellerInputsForSellOffer() {
        Offer offer = ValidationTestUtils.offer(false, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = ValidationTestUtils.rawTransactionInputs(btcWalletService,
                offer.getSellerSecurityDeposit().add(offer.getAmount()));

        assertSame(rawTransactionInputs, DepositTxValidation.checkMakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                offer));
    }

    @Test
    void checkTakersRawTransactionInputsRejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> DepositTxValidation.checkTakersRawTransactionInputs(null,
                mock(BtcWalletService.class),
                mock(Offer.class),
                Coin.valueOf(3000),
                Coin.valueOf(20_000)));
    }

    @Test
    void checkMakersRawTransactionInputsRejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> DepositTxValidation.checkMakersRawTransactionInputs(null,
                mock(BtcWalletService.class),
                mock(Offer.class)));
    }

    @Test
    void checkRawTransactionInputsAreNotMalleableAcceptsP2whInputs() {
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        RawTransactionInput rawTransactionInput = ValidationTestUtils.rawTransactionInput(Coin.valueOf(10_000));
        List<RawTransactionInput> rawTransactionInputs = List.of(rawTransactionInput);
        when(tradeWalletService.isP2WH(rawTransactionInput)).thenReturn(true);

        assertSame(rawTransactionInputs,
                DepositTxValidation.checkRawTransactionInputsAreNotMalleable(rawTransactionInputs, tradeWalletService));
        verify(tradeWalletService).isP2WH(rawTransactionInput);
    }

    @Test
    void checkRawTransactionInputsAreNotMalleableRejectsMalleableInputs() {
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        RawTransactionInput rawTransactionInput = ValidationTestUtils.rawTransactionInput(Coin.valueOf(10_000));
        List<RawTransactionInput> rawTransactionInputs = List.of(rawTransactionInput);
        when(tradeWalletService.isP2WH(rawTransactionInput)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkRawTransactionInputsAreNotMalleable(rawTransactionInputs,
                        tradeWalletService));
    }

    @Test
    void checkRawTransactionInputsAreNotMalleableRejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> DepositTxValidation.checkRawTransactionInputsAreNotMalleable(null,
                        mock(TradeWalletService.class)));
        assertThrows(NullPointerException.class,
                () -> DepositTxValidation.checkRawTransactionInputsAreNotMalleable(List.of(ValidationTestUtils.rawTransactionInput(Coin.SATOSHI)),
                        null));
    }

    /* --------------------------------------------------------------------- */
    // Peer input validation
    /* --------------------------------------------------------------------- */

    @Test
    void acceptsExactExpectedInputAmountForP2WHInputs() {
        List<RawTransactionInput> rawTransactionInputs = Arrays.asList(
                rawInput(parentTxWithP2WHOutput(40_000)),
                rawInput(parentTxWithP2WHOutput(60_000)));
        BtcWalletService btcWalletService = walletServiceFor(rawTransactionInputs);

        assertDoesNotThrow(() -> DepositTxValidation.validatePeersInputs(
                rawTransactionInputs,
                Coin.valueOf(100_000),
                btcWalletService,
                MAKER_ROLE));
    }

    @Test
    void rejectsInputAmountMismatch() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2WHOutput(100_000)));
        BtcWalletService btcWalletService = walletServiceFor(rawTransactionInputs);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.validatePeersInputs(
                        rawTransactionInputs,
                        Coin.valueOf(99_999),
                        btcWalletService,
                        MAKER_ROLE));
    }

    @Test
    void rejectsNullInputList() {
        assertThrows(NullPointerException.class,
                () -> DepositTxValidation.validatePeersInputs(null, Coin.valueOf(1), btcWalletService(), TAKER_ROLE));
    }

    @Test
    void rejectsEmptyInputList() {
        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.validatePeersInputs(List.of(), Coin.valueOf(1), btcWalletService(), TAKER_ROLE));
    }

    @Test
    void rejectsNullInput() {
        List<RawTransactionInput> rawTransactionInputs = Arrays.asList(rawInput(parentTxWithP2WHOutput(1)), null);
        BtcWalletService btcWalletService = walletServiceFor(rawTransactionInputs.get(0));

        assertThrows(NullPointerException.class,
                () -> DepositTxValidation.validatePeersInputs(
                        rawTransactionInputs,
                        Coin.valueOf(1),
                        btcWalletService,
                        TAKER_ROLE));
    }

    @Test
    void rejectsNullExpectedInputAmount() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2WHOutput(100_000)));

        assertThrows(NullPointerException.class,
                () -> DepositTxValidation.validatePeersInputs(rawTransactionInputs, null, btcWalletService(), MAKER_ROLE));
    }

    @Test
    void rejectsNonPositiveExpectedInputAmount() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2WHOutput(100_000)));

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.validatePeersInputs(rawTransactionInputs, Coin.ZERO, btcWalletService(), MAKER_ROLE));
    }

    @Test
    void rejectsInputValueMismatchWithParentTxOutput() {
        Transaction parentTx = parentTxWithP2WHOutput(100_000);
        RawTransactionInput rawTransactionInput = rawInputWithValue(parentTx, 100_001);
        BtcWalletService btcWalletService = walletServiceFor(rawTransactionInput);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.validatePeersInputs(
                        Collections.singletonList(rawTransactionInput),
                        Coin.valueOf(100_001),
                        btcWalletService,
                        TAKER_ROLE));
    }

    @Test
    void rejectsNonP2WHInput() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2pkhOutput(100_000)));
        BtcWalletService btcWalletService = walletServiceFor(rawTransactionInputs);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.validatePeersInputs(
                        rawTransactionInputs,
                        Coin.valueOf(100_000),
                        btcWalletService,
                        TAKER_ROLE));
    }

    @Test
    void rejectsMalformedParentTransaction() {
        RawTransactionInput rawTransactionInput = rawInputWithParentTransaction(new byte[]{1}, 100_000);
        BtcWalletService btcWalletService = btcWalletService();
        when(btcWalletService.getTxFromSerializedTx(rawTransactionInput.parentTransaction))
                .thenAnswer(invocation -> new Transaction(PARAMS, invocation.getArgument(0)));

        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> DepositTxValidation.validatePeersInputs(
                        Collections.singletonList(rawTransactionInput),
                        Coin.valueOf(100_000),
                        btcWalletService,
                        MAKER_ROLE));

        assertEquals(ArrayIndexOutOfBoundsException.class, exception.getCause().getClass());
    }

    private static BtcWalletService walletServiceFor(List<RawTransactionInput> rawTransactionInputs) {
        BtcWalletService btcWalletService = btcWalletService();
        rawTransactionInputs.forEach(rawTransactionInput -> {
            if (rawTransactionInput != null) {
                stubRawTransactionInput(btcWalletService, rawTransactionInput);
            }
        });
        return btcWalletService;
    }

    private static BtcWalletService walletServiceFor(RawTransactionInput rawTransactionInput) {
        return walletServiceFor(Collections.singletonList(rawTransactionInput));
    }

    private static void stubRawTransactionInput(BtcWalletService btcWalletService,
                                                RawTransactionInput rawTransactionInput) {
        Transaction parentTx = new Transaction(PARAMS, rawTransactionInput.parentTransaction);
        when(btcWalletService.getTxFromSerializedTx(rawTransactionInput.parentTransaction)).thenReturn(parentTx);
        when(btcWalletService.isP2WH(rawTransactionInput)).thenReturn(isP2WH(parentTx, rawTransactionInput));
    }

    private static boolean isP2WH(Transaction parentTx, RawTransactionInput rawTransactionInput) {
        Script.ScriptType scriptType = parentTx.getOutput(rawTransactionInput.index).getScriptPubKey().getScriptType();
        return scriptType == Script.ScriptType.P2WPKH || scriptType == Script.ScriptType.P2WSH;
    }

    private static RawTransactionInput rawInput(Transaction parentTx) {
        Transaction spendingTx = new Transaction(PARAMS);
        TransactionInput input = spendingTx.addInput(parentTx.getOutput(0));
        return new RawTransactionInput(input);
    }

    private static RawTransactionInput rawInputWithValue(Transaction parentTx, long value) {
        return rawInputWithParentTransaction(parentTx.bitcoinSerialize(), value);
    }

    private static RawTransactionInput rawInputWithParentTransaction(byte[] parentTransaction, long value) {
        return RawTransactionInput.fromProto(protobuf.RawTransactionInput.newBuilder()
                .setIndex(0)
                .setParentTransaction(ByteString.copyFrom(parentTransaction))
                .setValue(value)
                .build());
    }

    private static Transaction parentTxWithP2WHOutput(long value) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, ScriptBuilder.createEmpty());
        tx.addOutput(Coin.valueOf(value), SegwitAddress.fromKey(PARAMS, new ECKey()));
        return tx;
    }

    private static Transaction parentTxWithP2pkhOutput(long value) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, ScriptBuilder.createEmpty());
        Address address = Address.fromKey(PARAMS, new ECKey(), Script.ScriptType.P2PKH);
        tx.addOutput(Coin.valueOf(value), address);
        return tx;
    }


    static Transaction depositTx(Coin outputAmount, String addressString, long outpointIndex) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(new TransactionInput(PARAMS,
                transaction,
                new byte[]{},
                new TransactionOutPoint(PARAMS, outpointIndex, Sha256Hash.ZERO_HASH),
                outputAmount.add(Coin.SATOSHI)));
        transaction.addOutput(outputAmount, Address.fromString(PARAMS, addressString));
        return transaction;
    }

    static void addSignatureData(Transaction transaction, byte[] scriptSigProgram, byte[] witnessProgram) {
        transaction.getInput(0).setScriptSig(new ScriptBuilder().data(scriptSigProgram).build());
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, witnessProgram);
        transaction.getInput(0).setWitness(witness);
    }

    static Transaction copy(Transaction transaction) {
        return new Transaction(PARAMS, transaction.bitcoinSerialize());
    }
}

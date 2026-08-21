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
import bisq.core.btc.wallet.WalletUtils;
import bisq.core.offer.Offer;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.validation.exceptions.InvalidTxException;

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
    private static final String MAKER_FEE_TX_ID = ValidationTestUtils.VALID_TRANSACTION_ID;
    private static final String TAKER_FEE_TX_ID =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final String OTHER_TX_ID =
            "1111111111111111111111111111111111111111111111111111111111111111";
    private static final ECKey BUYER_MULTI_SIG_KEY = new ECKey();
    private static final ECKey SELLER_MULTI_SIG_KEY = new ECKey();

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
    // Canonical deposit tx shape
    /* --------------------------------------------------------------------- */

    @Test
    void checkCanonicalDepositTxShapeAcceptsCanonicalTx() {
        Transaction tx = canonicalTx();
        List<RawTransactionInput> p2wpkhInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(1_000)));

        assertSame(tx, DepositTxValidation.checkCanonicalDepositTxShape(tx, p2wpkhInputs, PARAMS));
    }

    @Test
    void checkCanonicalDepositTxShapeRejectsNonV1Tx() {
        Transaction tx = canonicalTx();
        tx.setVersion(2);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkCanonicalDepositTxShape(tx,
                        Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(1_000))),
                        PARAMS));
    }

    @Test
    void checkCanonicalDepositTxShapeRejectsNonZeroLockTime() {
        Transaction tx = canonicalTx();
        tx.setLockTime(1);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkCanonicalDepositTxShape(tx,
                        Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(1_000))),
                        PARAMS));
    }

    @Test
    void checkCanonicalDepositTxShapeRejectsRbfEnabledSequence() {
        Transaction tx = canonicalTx();
        tx.getInput(0).setSequenceNumber(0);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkCanonicalDepositTxShape(tx,
                        Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(1_000))),
                        PARAMS));
    }

    @Test
    void checkCanonicalDepositTxShapeAcceptsLockTimeOptInSequence() {
        Transaction tx = canonicalTx();
        tx.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE - 1);
        List<RawTransactionInput> p2wpkhInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(1_000)));

        assertSame(tx, DepositTxValidation.checkCanonicalDepositTxShape(tx, p2wpkhInputs, PARAMS));
    }

    @Test
    void checkCanonicalDepositTxShapeRejectsNonP2WPKHPeerInput() {
        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkCanonicalDepositTxShape(canonicalTx(),
                        Collections.singletonList(rawInput(parentTxWithP2pkhOutput(1_000))),
                        PARAMS));
    }

    @Test
    void checkCanonicalDepositTxShapeRejectsNullPeerInput() {
        assertThrows(NullPointerException.class,
                () -> DepositTxValidation.checkCanonicalDepositTxShape(canonicalTx(),
                        Collections.singletonList(null),
                        PARAMS));
    }

    /* --------------------------------------------------------------------- */
    // Maker prepared deposit tx
    /* --------------------------------------------------------------------- */

    @Test
    void checkMakersPreparedDepositTxAcceptsBuyerAsMakerTx() {
        Offer offer = offer(true,
                Coin.valueOf(10_000),
                Coin.valueOf(20_000),
                Coin.valueOf(10_000),
                Coin.valueOf(150_000));
        Coin tradeAmount = Coin.valueOf(100_000);
        Coin tradeTxFee = Coin.valueOf(5_000);
        List<RawTransactionInput> makerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(10_000)));
        List<RawTransactionInput> takerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(130_000)));
        Transaction preparedDepositTx = preparedDepositTx(
                orderedInputs(makerInputs, takerInputs, true),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                135_000);

        assertSame(preparedDepositTx,
                DepositTxValidation.checkMakersPreparedDepositTx(preparedDepositTx,
                        offer,
                        tradeAmount,
                        tradeTxFee,
                        makerInputs,
                        takerInputs,
                        BUYER_MULTI_SIG_KEY.getPubKey(),
                        SELLER_MULTI_SIG_KEY.getPubKey(),
                        PARAMS));
    }

    @Test
    void checkMakersPreparedDepositTxAcceptsSellerAsMakerTxWithExpectedChange() {
        Offer offer = offer(false,
                Coin.valueOf(10_000),
                Coin.valueOf(20_000),
                Coin.valueOf(10_000),
                Coin.valueOf(150_000));
        Coin tradeAmount = Coin.valueOf(100_000);
        Coin tradeTxFee = Coin.valueOf(5_000);
        List<RawTransactionInput> makerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(170_000)));
        List<RawTransactionInput> takerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(20_000)));
        Transaction preparedDepositTx = preparedDepositTx(
                orderedInputs(makerInputs, takerInputs, false),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                135_000,
                50_000);

        assertSame(preparedDepositTx,
                DepositTxValidation.checkMakersPreparedDepositTx(preparedDepositTx,
                        offer,
                        tradeAmount,
                        tradeTxFee,
                        makerInputs,
                        takerInputs,
                        SELLER_MULTI_SIG_KEY.getPubKey(),
                        BUYER_MULTI_SIG_KEY.getPubKey(),
                        PARAMS));
    }

    @Test
    void checkMakersPreparedDepositTxRejectsWrongInputOrder() {
        Offer offer = offer(false,
                Coin.valueOf(10_000),
                Coin.valueOf(20_000),
                Coin.valueOf(10_000),
                Coin.valueOf(150_000));
        Coin tradeAmount = Coin.valueOf(100_000);
        Coin tradeTxFee = Coin.valueOf(5_000);
        List<RawTransactionInput> makerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(170_000)));
        List<RawTransactionInput> takerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(20_000)));
        Transaction preparedDepositTx = preparedDepositTx(
                orderedInputs(makerInputs, takerInputs, true),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                135_000,
                50_000);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkMakersPreparedDepositTx(preparedDepositTx,
                        offer,
                        tradeAmount,
                        tradeTxFee,
                        makerInputs,
                        takerInputs,
                        SELLER_MULTI_SIG_KEY.getPubKey(),
                        BUYER_MULTI_SIG_KEY.getPubKey(),
                        PARAMS));
    }


    /* --------------------------------------------------------------------- */
    // Canonical final deposit tx fields
    /* --------------------------------------------------------------------- */

    @Test
    void checkCanonicalDepositTxFieldsAcceptsCanonicalTx() {
        Transaction tx = canonicalTx();

        assertSame(tx, DepositTxValidation.checkCanonicalDepositTxFields(tx));
    }

    @Test
    void checkCanonicalDepositTxFieldsRejectsNonV1Tx() {
        Transaction tx = canonicalTx();
        tx.setVersion(2);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkCanonicalDepositTxFields(tx));
    }

    @Test
    void checkCanonicalDepositTxFieldsRejectsNonZeroLockTime() {
        Transaction tx = canonicalTx();
        tx.setLockTime(1);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkCanonicalDepositTxFields(tx));
    }

    @Test
    void checkCanonicalDepositTxFieldsRejectsRbfEnabledSequence() {
        Transaction tx = canonicalTx();
        tx.getInput(0).setSequenceNumber(0);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkCanonicalDepositTxFields(tx));
    }

    @Test
    void checkMakersPreparedDepositTxRejectsWrongMultisigOutputAmount() {
        Offer offer = offer(false,
                Coin.valueOf(10_000),
                Coin.valueOf(20_000),
                Coin.valueOf(10_000),
                Coin.valueOf(150_000));
        Coin tradeAmount = Coin.valueOf(100_000);
        Coin tradeTxFee = Coin.valueOf(5_000);
        List<RawTransactionInput> makerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(170_000)));
        List<RawTransactionInput> takerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(20_000)));
        Transaction preparedDepositTx = preparedDepositTx(
                orderedInputs(makerInputs, takerInputs, false),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                134_999,
                50_000);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkMakersPreparedDepositTx(preparedDepositTx,
                        offer,
                        tradeAmount,
                        tradeTxFee,
                        makerInputs,
                        takerInputs,
                        SELLER_MULTI_SIG_KEY.getPubKey(),
                        BUYER_MULTI_SIG_KEY.getPubKey(),
                        PARAMS));
    }

    @Test
    void checkMakersPreparedDepositTxRejectsWrongMultisigOutputScript() {
        Offer offer = offer(false,
                Coin.valueOf(10_000),
                Coin.valueOf(20_000),
                Coin.valueOf(10_000),
                Coin.valueOf(150_000));
        Coin tradeAmount = Coin.valueOf(100_000);
        Coin tradeTxFee = Coin.valueOf(5_000);
        List<RawTransactionInput> makerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(170_000)));
        List<RawTransactionInput> takerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(20_000)));
        Transaction preparedDepositTx = preparedDepositTx(
                orderedInputs(makerInputs, takerInputs, false),
                new ECKey().getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                135_000,
                50_000);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkMakersPreparedDepositTx(preparedDepositTx,
                        offer,
                        tradeAmount,
                        tradeTxFee,
                        makerInputs,
                        takerInputs,
                        SELLER_MULTI_SIG_KEY.getPubKey(),
                        BUYER_MULTI_SIG_KEY.getPubKey(),
                        PARAMS));
    }

    @Test
    void checkMakersPreparedDepositTxRejectsExtraSiphonOutput() {
        Offer offer = offer(false,
                Coin.valueOf(10_000),
                Coin.valueOf(20_000),
                Coin.valueOf(10_000),
                Coin.valueOf(150_000));
        Coin tradeAmount = Coin.valueOf(100_000);
        Coin tradeTxFee = Coin.valueOf(5_000);
        List<RawTransactionInput> makerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(170_000)));
        List<RawTransactionInput> takerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(20_000)));
        Transaction preparedDepositTx = preparedDepositTx(
                orderedInputs(makerInputs, takerInputs, false),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                135_000,
                50_000,
                1_000);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkMakersPreparedDepositTx(preparedDepositTx,
                        offer,
                        tradeAmount,
                        tradeTxFee,
                        makerInputs,
                        takerInputs,
                        SELLER_MULTI_SIG_KEY.getPubKey(),
                        BUYER_MULTI_SIG_KEY.getPubKey(),
                        PARAMS));
    }

    @Test
    void checkMakersPreparedDepositTxRejectsWrongTradeFee() {
        Offer offer = offer(false,
                Coin.valueOf(10_000),
                Coin.valueOf(20_000),
                Coin.valueOf(10_000),
                Coin.valueOf(150_000));
        Coin tradeAmount = Coin.valueOf(100_000);
        Coin tradeTxFee = Coin.valueOf(5_000);
        List<RawTransactionInput> makerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(170_000)));
        List<RawTransactionInput> takerInputs = Collections.singletonList(rawInput(parentTxWithP2wpkhOutput(21_000)));
        Transaction preparedDepositTx = preparedDepositTx(
                orderedInputs(makerInputs, takerInputs, false),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                135_000,
                50_000);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkMakersPreparedDepositTx(preparedDepositTx,
                        offer,
                        tradeAmount,
                        tradeTxFee,
                        makerInputs,
                        takerInputs,
                        SELLER_MULTI_SIG_KEY.getPubKey(),
                        BUYER_MULTI_SIG_KEY.getPubKey(),
                        PARAMS));
    }

    /* --------------------------------------------------------------------- */
    // Deposit inputs
    /* --------------------------------------------------------------------- */

    @Test
    void checkDepositInputsAcceptsMakerThenTakerInputOrder() {
        Trade trade = tradeWithDepositInputs(MAKER_FEE_TX_ID, TAKER_FEE_TX_ID);

        assertSame(trade, DepositTxValidation.checkDepositInputs(trade));
    }

    @Test
    void checkDepositInputsAcceptsTakerThenMakerInputOrder() {
        Trade trade = tradeWithDepositInputs(TAKER_FEE_TX_ID, MAKER_FEE_TX_ID);

        assertSame(trade, DepositTxValidation.checkDepositInputs(trade));
    }

    @Test
    void checkDepositInputsRejectsInputMissingContractFeeTxId() {
        Trade trade = tradeWithDepositInputs(MAKER_FEE_TX_ID, OTHER_TX_ID);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkDepositInputs(trade));
    }

    @Test
    void checkDepositInputsRejectsNullTrade() {
        assertThrows(NullPointerException.class,
                () -> DepositTxValidation.checkDepositInputs(null));
    }

    @Test
    void checkDepositInputsRejectsUnexpectedInputCount() {
        Transaction depositTx = new Transaction(PARAMS);
        depositTx.addInput(transactionInput(depositTx, MAKER_FEE_TX_ID));
        depositTx.addOutput(Coin.valueOf(1_000), SegwitAddress.fromKey(PARAMS, new ECKey()));
        Trade trade = tradeWithDepositTx(depositTx);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkDepositInputs(trade));
    }

    @Test
    void checkDepositInputsRejectsStructurallyInvalidDepositTx() {
        Transaction depositTx = new Transaction(PARAMS);
        depositTx.addInput(transactionInput(depositTx, MAKER_FEE_TX_ID));
        depositTx.addInput(transactionInput(depositTx, TAKER_FEE_TX_ID));
        Trade trade = tradeWithDepositTx(depositTx);

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkDepositInputs(trade));
    }

    @Test
    void validateDepositInputsWrapsValidationFailures() {
        Trade trade = tradeWithDepositInputs(MAKER_FEE_TX_ID, OTHER_TX_ID);

        assertThrows(InvalidTxException.class,
                () -> DepositTxValidation.validateDepositInputs(trade));
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

    // Regression: buyer-as-taker funded from a legacy P2PKH UTXO signs the input,
    // then bitcoinSerialize(false) strips witness but the scriptSig remains.
    // Validator rejects → trade aborts mid-protocol. The wallet must gate this
    // earlier; this test pins current validator behavior.
    @Test
    void checkTransactionIsUnsignedRejectsLegacyP2pkhSignedInputAfterWitnessStrip() {
        assertThrows(IllegalArgumentException.class, () -> DepositTxValidation.checkTransactionIsUnsigned(
                ValidationTestUtils.serializedTransactionWithLegacyP2pkhSignedInputAfterWitnessStrip(),
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
    void checkRawTransactionInputsAreNotMalleableAcceptsP2wpkhInputs() {
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        RawTransactionInput rawTransactionInput = ValidationTestUtils.rawTransactionInput(Coin.valueOf(10_000));
        List<RawTransactionInput> rawTransactionInputs = List.of(rawTransactionInput);
        when(tradeWalletService.isP2WPKH(rawTransactionInput)).thenReturn(true);

        assertSame(rawTransactionInputs,
                DepositTxValidation.checkRawTransactionInputsAreNotMalleable(rawTransactionInputs, tradeWalletService));
        verify(tradeWalletService).isP2WPKH(rawTransactionInput);
    }

    @Test
    void checkRawTransactionInputsAreNotMalleableRejectsMalleableInputs() {
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        RawTransactionInput rawTransactionInput = ValidationTestUtils.rawTransactionInput(Coin.valueOf(10_000));
        List<RawTransactionInput> rawTransactionInputs = List.of(rawTransactionInput);
        when(tradeWalletService.isP2WPKH(rawTransactionInput)).thenReturn(false);

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
    void acceptsExactExpectedInputAmountForP2WPKHInputs() {
        List<RawTransactionInput> rawTransactionInputs = Arrays.asList(
                rawInput(parentTxWithP2wpkhOutput(40_000)),
                rawInput(parentTxWithP2wpkhOutput(60_000)));
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
                rawInput(parentTxWithP2wpkhOutput(100_000)));
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
        List<RawTransactionInput> rawTransactionInputs = Arrays.asList(rawInput(parentTxWithP2wpkhOutput(1)), null);
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
                rawInput(parentTxWithP2wpkhOutput(100_000)));

        assertThrows(NullPointerException.class,
                () -> DepositTxValidation.validatePeersInputs(rawTransactionInputs, null, btcWalletService(), MAKER_ROLE));
    }

    @Test
    void rejectsNonPositiveExpectedInputAmount() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2wpkhOutput(100_000)));

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.validatePeersInputs(rawTransactionInputs, Coin.ZERO, btcWalletService(), MAKER_ROLE));
    }

    @Test
    void rejectsInputValueMismatchWithParentTxOutput() {
        Transaction parentTx = parentTxWithP2wpkhOutput(100_000);
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
    void rejectsNonP2WPKHInput() {
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
    void rejectsP2WSHInput() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2wshOutput(100_000)));
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

    @Test
    void rejectsMixedP2WPKHAndNonP2WPKHInputs() {
        List<RawTransactionInput> rawTransactionInputs = Arrays.asList(
                rawInput(parentTxWithP2wpkhOutput(40_000)),
                rawInput(parentTxWithP2pkhOutput(60_000)));
        BtcWalletService btcWalletService = walletServiceFor(rawTransactionInputs);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.validatePeersInputs(
                        rawTransactionInputs,
                        Coin.valueOf(100_000),
                        btcWalletService,
                        TAKER_ROLE));

        assertEquals(true, exception.getMessage().contains("position 1"),
                "error must point at the offending input index, was: " + exception.getMessage());
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

    private static Offer offer(boolean isBuyOffer,
                               Coin buyerSecurityDeposit,
                               Coin sellerSecurityDeposit,
                               Coin offerMinAmount,
                               Coin offerAmount) {
        Offer offer = ValidationTestUtils.offer(isBuyOffer,
                buyerSecurityDeposit,
                sellerSecurityDeposit,
                offerAmount);
        when(offer.getMinAmount()).thenReturn(offerMinAmount);
        return offer;
    }

    private static List<RawTransactionInput> orderedInputs(List<RawTransactionInput> makerInputs,
                                                           List<RawTransactionInput> takerInputs,
                                                           boolean makerFirst) {
        if (makerFirst) {
            return Arrays.asList(makerInputs.get(0), takerInputs.get(0));
        }
        return Arrays.asList(takerInputs.get(0), makerInputs.get(0));
    }

    private static Transaction preparedDepositTx(List<RawTransactionInput> orderedInputs,
                                                 byte[] buyerPubKey,
                                                 byte[] sellerPubKey,
                                                 long... outputValues) {
        Transaction tx = new Transaction(PARAMS);
        for (RawTransactionInput input : orderedInputs) {
            tx.addInput(new TransactionInput(PARAMS,
                    tx,
                    new byte[]{},
                    WalletUtils.getConnectedOutPoint(input, PARAMS),
                    Coin.valueOf(input.value)));
        }

        tx.addOutput(Coin.valueOf(outputValues[0]), multiSigOutputScript(buyerPubKey, sellerPubKey));
        for (int i = 1; i < outputValues.length; i++) {
            tx.addOutput(Coin.valueOf(outputValues[i]), SegwitAddress.fromKey(PARAMS, new ECKey()));
        }
        return tx;
    }

    private static Script multiSigOutputScript(byte[] buyerPubKey, byte[] sellerPubKey) {
        return ScriptBuilder.createP2WSHOutputScript(
                ScriptBuilder.createMultiSigOutputScript(2,
                        Arrays.asList(ECKey.fromPublicOnly(sellerPubKey), ECKey.fromPublicOnly(buyerPubKey))));
    }

    private static BtcWalletService walletServiceFor(RawTransactionInput rawTransactionInput) {
        return walletServiceFor(Collections.singletonList(rawTransactionInput));
    }

    private static void stubRawTransactionInput(BtcWalletService btcWalletService,
                                                RawTransactionInput rawTransactionInput) {
        Transaction parentTx = new Transaction(PARAMS, rawTransactionInput.parentTransaction);
        when(btcWalletService.getTxFromSerializedTx(rawTransactionInput.parentTransaction)).thenReturn(parentTx);
        when(btcWalletService.isP2WPKH(rawTransactionInput)).thenReturn(WalletUtils.isP2WPKH(rawTransactionInput, PARAMS));
    }

    private static RawTransactionInput rawInput(Transaction parentTx) {
        Transaction spendingTx = new Transaction(PARAMS);
        TransactionInput input = spendingTx.addInput(parentTx.getOutput(0));
        return new RawTransactionInput(input);
    }

    private static Trade tradeWithDepositInputs(String input0TxId, String input1TxId) {
        return tradeWithDepositTx(depositTxWithInputs(input0TxId, input1TxId));
    }

    private static Trade tradeWithDepositTx(Transaction depositTx) {
        OfferPayload offerPayload = mock(OfferPayload.class);
        when(offerPayload.getOfferFeePaymentTxId()).thenReturn(MAKER_FEE_TX_ID);

        Contract contract = mock(Contract.class);
        when(contract.getOfferPayload()).thenReturn(offerPayload);
        when(contract.getTakerFeeTxID()).thenReturn(TAKER_FEE_TX_ID);

        Trade trade = mock(Trade.class);
        when(trade.getDepositTx()).thenReturn(depositTx);
        when(trade.getContract()).thenReturn(contract);
        return trade;
    }

    private static Transaction depositTxWithInputs(String input0TxId, String input1TxId) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(transactionInput(transaction, input0TxId));
        transaction.addInput(transactionInput(transaction, input1TxId));
        transaction.addOutput(Coin.valueOf(1_000), SegwitAddress.fromKey(PARAMS, new ECKey()));
        return transaction;
    }

    private static TransactionInput transactionInput(Transaction transaction, String txId) {
        return new TransactionInput(PARAMS,
                transaction,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.wrap(txId)),
                Coin.valueOf(1_000));
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

    private static Transaction parentTxWithP2wpkhOutput(long value) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, ScriptBuilder.createEmpty());
        tx.addOutput(Coin.valueOf(value), SegwitAddress.fromKey(PARAMS, new ECKey()));
        return tx;
    }

    private static Transaction parentTxWithP2wshOutput(long value) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, ScriptBuilder.createEmpty());
        tx.addOutput(Coin.valueOf(value),
                ScriptBuilder.createP2WSHOutputScript(ScriptBuilder.createMultiSigOutputScript(1, List.of(new ECKey()))));
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

    private static Transaction canonicalTx() {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(new TransactionInput(PARAMS,
                tx,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.ZERO_HASH),
                Coin.valueOf(2_000)));
        return tx;
    }
}

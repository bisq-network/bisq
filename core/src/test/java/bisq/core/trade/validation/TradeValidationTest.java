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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.validation;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Base64;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;

import java.security.KeyPair;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.TradeValidationTestUtils.btcWalletService;
import static bisq.core.trade.validation.TradeValidationTestUtils.pubKeyRing;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TradeValidationTest {
    static final byte[] ACCOUNT_AGE_WITNESS_NONCE =
            "account-age-witness-nonce".getBytes(StandardCharsets.UTF_8);


    @Test
    void checkTradeIdAcceptsMatchingTradeMessageTradeId() {
        String tradeId = "trade-id";

        assertSame(tradeId, TradeValidation.checkTradeId(tradeId, TradeValidationTestUtils.tradeMessage(tradeId)));
    }

    @Test
    void checkTradeIdRejectsMismatchingTradeMessageTradeId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkTradeId("trade-id", TradeValidationTestUtils.tradeMessage("other-trade-id")));

        assertEquals("TradeId trade-id is not valid", exception.getMessage());
    }

    @Test
    void checkTradeIdRejectsNullTradeId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTradeId(null, TradeValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidReturnsTrueForMatchingTradeMessageTradeId() {
        assertEquals(true, TradeValidation.isTradeIdValid("trade-id", TradeValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidReturnsFalseForMismatchingTradeMessageTradeId() {
        assertEquals(false, TradeValidation.isTradeIdValid("trade-id", TradeValidationTestUtils.tradeMessage("other-trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.isTradeIdValid(null, TradeValidationTestUtils.tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeMessage() {
        assertThrows(NullPointerException.class, () -> TradeValidation.isTradeIdValid("trade-id", null));
    }


    @Test
    void checkBitcoinAddressAcceptsAddressForWalletNetwork() {
        String bitcoinAddress = SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString();

        assertSame(bitcoinAddress, TradeValidation.checkBitcoinAddress(bitcoinAddress,
                btcWalletService()));
    }

    @Test
    void checkBitcoinAddressRejectsInvalidAddress() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBitcoinAddress("not-a-bitcoin-address",
                btcWalletService()));
    }

    @Test
    void checkBitcoinAddressRejectsAddressFromDifferentNetwork() {
        String testnetAddress = SegwitAddress.fromKey(TestNet3Params.get(), new ECKey()).toString();

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBitcoinAddress(testnetAddress,
                btcWalletService()));
    }

    @Test
    void checkBitcoinAddressRejectsNullAddress() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkBitcoinAddress(null,
                btcWalletService()));
    }

    @Test
    void checkBitcoinAddressRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkBitcoinAddress(
                SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString(),
                null));
    }

    @Test
    void checkSerializedTransactionAcceptsValidSerializedTransaction() {
        byte[] serializedTransaction = TradeValidationTestUtils.serializedTransaction();

        assertSame(serializedTransaction, TradeValidation.checkSerializedTransaction(serializedTransaction,
                btcWalletService()));
    }

    @Test
    void checkSerializedTransactionRejectsMalformedSerializedTransaction() {
        assertThrows(RuntimeException.class, () -> TradeValidation.checkSerializedTransaction(new byte[]{1, 2, 3},
                btcWalletService()));
    }

    @Test
    void checkSerializedTransactionRejectsNullSerializedTransaction() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkSerializedTransaction(null,
                btcWalletService()));
    }

    @Test
    void checkTransactionAcceptsValidTransaction() {
        Transaction transaction = TradeValidationTestUtils.transaction(new byte[]{});

        assertSame(transaction, TradeValidation.checkTransaction(transaction));
    }

    @Test
    void checkTransactionRejectsStructurallyInvalidTransaction() {
        Transaction transaction = TradeValidationTestUtils.transactionWithoutOutputs();

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTransaction(transaction));
    }

    @Test
    void checkTransactionRejectsNullTransaction() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTransaction(null));
    }

    @Test
    void toTransactionParsesValidSerializedVerifiedTransaction() {
        byte[] serializedTransaction = TradeValidationTestUtils.serializedTransaction();

        assertArrayEquals(serializedTransaction,
                TradeValidation.toVerifiedTransaction(serializedTransaction, btcWalletService())
                        .bitcoinSerialize());
    }

    @Test
    void toVerifiedTransactionRejectsMalformedSerializedTransaction() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.toVerifiedTransaction(new byte[]{1, 2, 3},
                btcWalletService()));
    }

    @Test
    void toVerifiedTransactionRejectsStructurallyInvalidTransaction() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.toVerifiedTransaction(
                TradeValidationTestUtils.serializedTransactionWithoutOutputs(),
                btcWalletService()));
    }

    @Test
    void toVerifiedTransactionRejectsNullSerializedTransaction() {
        assertThrows(NullPointerException.class, () -> TradeValidation.toVerifiedTransaction(null,
                btcWalletService()));
    }

    @Test
    void toVerifiedTransactionRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> TradeValidation.toVerifiedTransaction(
                TradeValidationTestUtils.serializedTransaction(),
                null));
    }

    @Test
    void checkTransactionIsUnsignedAcceptsValidUnsignedTransaction() {
        byte[] depositTxWithoutWitnesses = TradeValidationTestUtils.serializedTransaction();

        assertSame(depositTxWithoutWitnesses,
                TradeValidation.checkTransactionIsUnsigned(depositTxWithoutWitnesses,
                        btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsMalformedSerializedTransaction() {
        assertThrows(RuntimeException.class, () -> TradeValidation.checkTransactionIsUnsigned(
                new byte[]{1, 2, 3},
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsStructurallyInvalidTransaction() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTransactionIsUnsigned(
                TradeValidationTestUtils.serializedTransactionWithoutOutputs(),
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsTransactionWithScriptSig() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTransactionIsUnsigned(
                TradeValidationTestUtils.serializedTransactionWithScriptSig(),
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsTransactionWithWitness() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTransactionIsUnsigned(
                TradeValidationTestUtils.serializedTransactionWithWitness(),
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsNullTransaction() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTransactionIsUnsigned(null,
                btcWalletService()));
    }

    @Test
    void checkTransactionIsUnsignedRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTransactionIsUnsigned(
                TradeValidationTestUtils.serializedTransaction(),
                null));
    }

    @Test
    void checkTransactionIdAcceptsValidTransactionId() {
        assertEquals(TradeValidationTestUtils.VALID_TRANSACTION_ID, TradeValidation.checkTransactionId(TradeValidationTestUtils.VALID_TRANSACTION_ID));
    }

    @Test
    void checkTransactionIdAcceptsUpperCaseTransactionId() {
        String transactionId = TradeValidationTestUtils.VALID_TRANSACTION_ID.toUpperCase(Locale.ROOT);
        assertEquals(transactionId.toLowerCase(Locale.ROOT), TradeValidation.checkTransactionId(transactionId));
    }

    @Test
    void checkTransactionIdRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTransactionId(
                TradeValidationTestUtils.VALID_TRANSACTION_ID.substring(1)));
    }

    @Test
    void checkTransactionIdRejectsNonHexTransactionId() {
        String transactionId = TradeValidationTestUtils.VALID_TRANSACTION_ID.substring(0, TradeValidationTestUtils.VALID_TRANSACTION_ID.length() - 1) + "g";

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTransactionId(transactionId));
    }

    @Test
    void checkTransactionIdRejectsNullTransactionId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTransactionId(null));
    }

    @Test
    void checkDerEncodedEcdsaSignatureAcceptsStrictDerEncodedCanonicalSignature() {
        byte[] bitcoinSignature = TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ONE);

        assertSame(bitcoinSignature, TradeValidation.checkDerEncodedEcdsaSignature(bitcoinSignature));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsNullSignature() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkDerEncodedEcdsaSignature(null));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsEmptySignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkDerEncodedEcdsaSignature(new byte[0]));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsMalformedSignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkDerEncodedEcdsaSignature(new byte[]{1, 2, 3}));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsNonStrictDerEncoding() {
        byte[] bitcoinSignature = TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ONE);
        byte[] bitcoinSignatureWithTrailingData = Arrays.copyOf(bitcoinSignature, bitcoinSignature.length + 1);
        bitcoinSignatureWithTrailingData[bitcoinSignature.length] = 1;

        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkDerEncodedEcdsaSignature(bitcoinSignatureWithTrailingData));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsValuesOutsideCurveOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkDerEncodedEcdsaSignature(TradeValidationTestUtils.bitcoinSignature(BigInteger.ZERO, BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkDerEncodedEcdsaSignature(TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ZERO)));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkDerEncodedEcdsaSignature(TradeValidationTestUtils.bitcoinSignature(ECKey.CURVE.getN(), BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkDerEncodedEcdsaSignature(TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, ECKey.CURVE.getN())));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsNonCanonicalSValue() {
        byte[] bitcoinSignature = TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, ECKey.CURVE.getN().subtract(BigInteger.ONE));

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkDerEncodedEcdsaSignature(bitcoinSignature));
    }

    @Test
    void checkLockTimeAcceptsExpectedLockTimeAndAllowedDeviation() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getBestChainHeight()).thenReturn(1_000);
        long expectedLockTime = 1_000 + Restrictions.getLockTime(true);

        assertEquals(expectedLockTime, TradeValidation.checkLockTime(expectedLockTime,
                true,
                btcWalletService,
                true));
        assertEquals(expectedLockTime + TradeValidation.MAX_LOCKTIME_BLOCK_DEVIATION,
                TradeValidation.checkLockTime(expectedLockTime + TradeValidation.MAX_LOCKTIME_BLOCK_DEVIATION,
                        true,
                        btcWalletService,
                        true));
    }

    @Test
    void checkLockTimeRejectsLockTimeBeyondAllowedDeviation() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getBestChainHeight()).thenReturn(1_000);
        long invalidLockTime = 1_000 + Restrictions.getLockTime(false) +
                TradeValidation.MAX_LOCKTIME_BLOCK_DEVIATION + 1;

        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkLockTime(invalidLockTime, false, btcWalletService, true));
    }

    @Test
    void checkLockTimeSkipsHeightToleranceOnNonMainnet() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getBestChainHeight()).thenReturn(1_000);
        long lockTimeOutsideMainnetTolerance = 1_000 + Restrictions.getLockTime(false) +
                TradeValidation.MAX_LOCKTIME_BLOCK_DEVIATION + 1;

        assertEquals(lockTimeOutsideMainnetTolerance,
                TradeValidation.checkLockTime(lockTimeOutsideMainnetTolerance, false, btcWalletService, false));
    }

    @Test
    void checkLockTimeRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkLockTime(1, true, null, true));
    }

    @Test
    void checkLockTimeRejectsNonPositiveLockTime() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);

        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkLockTime(0, true, btcWalletService, true));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkLockTime(-1, true, btcWalletService, true));
    }

    @Test
    void checkAccountAgeWitnessSignatureAcceptsSignatureOfNonce() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = pubKeyRing(signatureKeyPair);
        byte[] accountAgeWitnessSignature = Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE);

        assertSame(accountAgeWitnessSignature, TradeValidation.checkSignature(
                accountAgeWitnessSignature,
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing.getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsSignatureOfDifferentNonce() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = pubKeyRing(signatureKeyPair);
        byte[] accountAgeWitnessSignature = Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE);
        byte[] otherNonce = "other-nonce".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkSignature(
                accountAgeWitnessSignature,
                otherNonce,
                pubKeyRing.getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsSignatureFromDifferentPubKey() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = pubKeyRing(Sig.generateKeyPair());
        byte[] accountAgeWitnessSignature = Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE);

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkSignature(
                accountAgeWitnessSignature,
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing.getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullSignature() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkSignature(null,
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing(Sig.generateKeyPair()).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsEmptySignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkSignature(new byte[0],
                ACCOUNT_AGE_WITNESS_NONCE,
                pubKeyRing(Sig.generateKeyPair()).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullNonce() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();

        assertThrows(NullPointerException.class, () -> TradeValidation.checkSignature(
                Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE),
                null,
                pubKeyRing(signatureKeyPair).getSignaturePubKey()));
    }

    @Test
    void checkAccountAgeWitnessSignatureRejectsNullPubKeyRing() throws CryptoException {
        KeyPair signatureKeyPair = Sig.generateKeyPair();

        assertThrows(NullPointerException.class, () -> TradeValidation.checkSignature(
                Sig.sign(signatureKeyPair.getPrivate(), ACCOUNT_AGE_WITNESS_NONCE),
                ACCOUNT_AGE_WITNESS_NONCE,
                null));
    }

    @Test
    void checkBase64SignatureAcceptsBase64EncodedSignature() {
        byte[] signature = new byte[]{1, 2, 3};
        String signatureBase64 = Base64.encode(signature);

        assertEquals(signatureBase64, TradeValidation.checkBase64Signature(signatureBase64));
        assertArrayEquals(signature, TradeValidation.toDecodedSignature(signatureBase64));
    }

    @Test
    void checkBase64SignatureRejectsInvalidBase64EncodedSignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBase64Signature("not base64"));
    }

    @Test
    void checkBase64SignatureRejectsNullSignature() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkBase64Signature(null));
    }

    @Test
    void checkBase64SignatureRejectsBlankSignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBase64Signature(" "));
    }

    @Test
    void checkPeersDateAcceptsDateWithinAllowedRange() {
        long now = System.currentTimeMillis();

        assertEquals(now, TradeValidation.checkPeersDate(now));
        assertEquals(now - TradeValidation.MAX_DATE_DEVIATION + 60_000,
                TradeValidation.checkPeersDate(now - TradeValidation.MAX_DATE_DEVIATION + 60_000));
        assertEquals(now + TradeValidation.MAX_DATE_DEVIATION - 60_000,
                TradeValidation.checkPeersDate(now + TradeValidation.MAX_DATE_DEVIATION - 60_000));
    }

    @Test
    void checkPeersDateRejectsDateOlderThanAllowedRange() {
        long currentDate = System.currentTimeMillis() - TradeValidation.MAX_DATE_DEVIATION - 60_000;

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(currentDate));
    }

    @Test
    void checkPeersDateRejectsDateNewerThanAllowedRange() {
        long currentDate = System.currentTimeMillis() + TradeValidation.MAX_DATE_DEVIATION + 60_000;

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(currentDate));
    }

    @Test
    void checkPeersDateRejectsZeroDate() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkPeersDate(0));
    }

    @Test
    void getCheckedMediatorPubKeyRingReturnsAcceptedMediatorPubKeyRing() {
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        PubKeyRing mediatorPubKeyRing = pubKeyRing(Sig.generateKeyPair());
        User user = TradeValidationTestUtils.userWithAcceptedMediator(mediatorNodeAddress,
                TradeValidationTestUtils.mediator(mediatorNodeAddress, mediatorPubKeyRing));

        assertSame(mediatorPubKeyRing, TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsNullMediatorNodeAddress() {
        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(null, mock(User.class)));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsNullUser() {
        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(new NodeAddress("mediator.onion", 9999), null));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsUnknownMediator() {
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        User user = TradeValidationTestUtils.userWithAcceptedMediator(mediatorNodeAddress, null);

        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsMediatorWithoutPubKeyRing() {
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        User user = TradeValidationTestUtils.userWithAcceptedMediator(mediatorNodeAddress, TradeValidationTestUtils.mediator(mediatorNodeAddress, null));

        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user));
    }


    @Test
    void checkTakersRawTransactionInputsAcceptsSellerInputsForBuyOffer() {
        Coin tradeAmount = Coin.valueOf(20_000);
        Coin tradeTxFee = Coin.valueOf(300);
        Offer offer = TradeValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = TradeValidationTestUtils.trade(offer, tradeTxFee);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = TradeValidationTestUtils.rawTransactionInputs(btcWalletService,
                offer.getSellerSecurityDeposit()
                        .add(tradeAmount)
                        .add(tradeTxFee.multiply(2)));

        assertSame(rawTransactionInputs, TradeValidation.checkTakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                trade.getOffer(),
                trade.getTradeTxFee(),
                tradeAmount));
    }

    @Test
    void checkTakersRawTransactionInputsAcceptsBuyerInputsForSellOffer() {
        Coin tradeAmount = Coin.valueOf(20_000);
        Coin tradeTxFee = Coin.valueOf(300);
        Offer offer = TradeValidationTestUtils.offer(false, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = TradeValidationTestUtils.trade(offer, tradeTxFee);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = TradeValidationTestUtils.rawTransactionInputs(btcWalletService,
                offer.getBuyerSecurityDeposit().add(tradeTxFee.multiply(2)));

        assertSame(rawTransactionInputs, TradeValidation.checkTakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                trade.getOffer(),
                trade.getTradeTxFee(),
                tradeAmount));
    }

    @Test
    void checkMakersRawTransactionInputsAcceptsBuyerInputsForBuyOffer() {
        Offer offer = TradeValidationTestUtils.offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = TradeValidationTestUtils.rawTransactionInputs(btcWalletService,
                offer.getBuyerSecurityDeposit());

        assertSame(rawTransactionInputs, TradeValidation.checkMakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                offer));
    }

    @Test
    void checkMakersRawTransactionInputsAcceptsSellerInputsForSellOffer() {
        Offer offer = TradeValidationTestUtils.offer(false, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = TradeValidationTestUtils.rawTransactionInputs(btcWalletService,
                offer.getSellerSecurityDeposit().add(offer.getAmount()));

        assertSame(rawTransactionInputs, TradeValidation.checkMakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                offer));
    }

    @Test
    void checkTakersRawTransactionInputsRejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTakersRawTransactionInputs(null,
                mock(BtcWalletService.class),
                mock(Offer.class),
                Coin.valueOf(3000),
                Coin.valueOf(20_000)));
    }

    @Test
    void checkMakersRawTransactionInputsRejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkMakersRawTransactionInputs(null,
                mock(BtcWalletService.class),
                mock(Offer.class)));
    }

    @Test
    void checkRawTransactionInputsAreNotMalleableAcceptsP2WPKHInputs() {
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        RawTransactionInput rawTransactionInput = TradeValidationTestUtils.rawTransactionInput(Coin.valueOf(10_000));
        List<RawTransactionInput> rawTransactionInputs = List.of(rawTransactionInput);
        when(tradeWalletService.isP2WPKH(rawTransactionInput)).thenReturn(true);

        assertSame(rawTransactionInputs,
                TradeValidation.checkRawTransactionInputsAreNotMalleable(rawTransactionInputs, tradeWalletService));
        verify(tradeWalletService).isP2WPKH(rawTransactionInput);
    }

    @Test
    void checkRawTransactionInputsAreNotMalleableRejectsMalleableInputs() {
        TradeWalletService tradeWalletService = mock(TradeWalletService.class);
        RawTransactionInput rawTransactionInput = TradeValidationTestUtils.rawTransactionInput(Coin.valueOf(10_000));
        List<RawTransactionInput> rawTransactionInputs = List.of(rawTransactionInput);
        when(tradeWalletService.isP2WPKH(rawTransactionInput)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkRawTransactionInputsAreNotMalleable(rawTransactionInputs,
                        tradeWalletService));
    }

    @Test
    void checkRawTransactionInputsAreNotMalleableRejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkRawTransactionInputsAreNotMalleable(null,
                        mock(TradeWalletService.class)));
        assertThrows(NullPointerException.class,
                () -> TradeValidation.checkRawTransactionInputsAreNotMalleable(List.of(TradeValidationTestUtils.rawTransactionInput(Coin.SATOSHI)),
                        null));
    }


    @Test
    void checkByteArrayWithExpectedAcceptsMatchingByteArrays() {
        byte[] current = new byte[]{1, 2, 3};

        assertSame(current, TradeValidationUtils.checkByteArrayWithExpected(current, new byte[]{1, 2, 3}));
    }

    @Test
    void checkByteArrayWithExpectedRejectsMismatchingByteArrays() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidationUtils.checkByteArrayWithExpected(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));

        assertEquals("current is not matching expected. current=010203, expected=010204", exception.getMessage());
    }

    @Test
    void checkByteArrayWithExpectedRejectsNullAndEmptyByteArrays() {
        assertThrows(NullPointerException.class,
                () -> TradeValidationUtils.checkByteArrayWithExpected(null, new byte[]{1}));
        assertThrows(NullPointerException.class,
                () -> TradeValidationUtils.checkByteArrayWithExpected(new byte[]{1}, null));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidationUtils.checkByteArrayWithExpected(new byte[0], new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidationUtils.checkByteArrayWithExpected(new byte[]{1}, new byte[0]));
    }
}

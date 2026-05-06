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
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.trade.TradeFeeFactory;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Base64;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.ScriptBuilder;

import java.security.KeyPair;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TradeValidationTest {
    private static final Coin OFFER_MIN_AMOUNT = Coin.valueOf(1_000);
    private static final Coin OFFER_MAX_AMOUNT = Coin.valueOf(5_000);
    private static final int GENESIS_HEIGHT = 102;
    private static final int GRID_SIZE = DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE;
    private static final String VALID_TRANSACTION_ID =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final byte[] ACCOUNT_AGE_WITNESS_NONCE =
            "account-age-witness-nonce".getBytes(StandardCharsets.UTF_8);

    @Test
    void checkTradeAmountAcceptsOfferBoundsAndValuesBetweenThem() {
        Coin tradeAmount = Coin.valueOf(3_000);

        assertSame(OFFER_MIN_AMOUNT,
                TradeValidation.checkTradeAmount(OFFER_MIN_AMOUNT, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertSame(tradeAmount,
                TradeValidation.checkTradeAmount(tradeAmount, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertSame(OFFER_MAX_AMOUNT,
                TradeValidation.checkTradeAmount(OFFER_MAX_AMOUNT, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
    }

    @Test
    void checkTradeAmountRejectsAmountsBelowOfferMinimum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkTradeAmount(Coin.valueOf(999), OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));

        assertEquals("Trade amount must not be less than minimum offer amount. " +
                        "tradeAmount=0.00000999 BTC, offerMinAmount=0.00001 BTC",
                exception.getMessage());
    }

    @Test
    void checkTradeAmountRejectsAmountsAboveOfferMaximum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkTradeAmount(Coin.valueOf(5_001), OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));

        assertEquals("Trade amount must not be higher than maximum offer amount. " +
                        "tradeAmount=0.00005001 BTC, offerMaxAmount=0.00005 BTC",
                exception.getMessage());
    }

    @Test
    void checkTakersTradePriceAcceptsVerifiedPrice() throws Exception {
        long takersTradePrice = 50_000_000L;
        Offer offer = mock(Offer.class);
        when(offer.isUseMarketBasedPrice()).thenReturn(true);

        assertEquals(takersTradePrice, TradeValidation.checkTakersTradePrice(takersTradePrice,
                mock(PriceFeedService.class),
                offer));
        verify(offer).verifyTakersTradePrice(takersTradePrice);
    }

    @Test
    void checkTakersTradePriceWrapsOfferPriceValidationFailure() throws Exception {
        long takersTradePrice = 50_000_000L;
        Offer offer = mock(Offer.class);
        doThrow(new TradePriceOutOfToleranceException("price outside tolerance"))
                .when(offer)
                .verifyTakersTradePrice(takersTradePrice);

        assertThrows(RuntimeException.class, () -> TradeValidation.checkTakersTradePrice(takersTradePrice,
                mock(PriceFeedService.class),
                offer));
    }

    @Test
    void checkTradeIdAcceptsMatchingTradeMessageTradeId() {
        String tradeId = "trade-id";

        assertSame(tradeId, TradeValidation.checkTradeId(tradeId, tradeMessage(tradeId)));
    }

    @Test
    void checkTradeIdRejectsMismatchingTradeMessageTradeId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkTradeId("trade-id", tradeMessage("other-trade-id")));

        assertEquals("TradeId trade-id is not valid", exception.getMessage());
    }

    @Test
    void checkTradeIdRejectsNullTradeId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTradeId(null, tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidReturnsTrueForMatchingTradeMessageTradeId() {
        assertEquals(true, TradeValidation.isTradeIdValid("trade-id", tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidReturnsFalseForMismatchingTradeMessageTradeId() {
        assertEquals(false, TradeValidation.isTradeIdValid("trade-id", tradeMessage("other-trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.isTradeIdValid(null, tradeMessage("trade-id")));
    }

    @Test
    void isTradeIdValidRejectsNullTradeMessage() {
        assertThrows(NullPointerException.class, () -> TradeValidation.isTradeIdValid("trade-id", null));
    }

    @Test
    void checkMultiSigPubKeyAcceptsCompressedPublicKey() {
        byte[] multiSigPubKey = new ECKey().getPubKey();

        assertEquals(33, multiSigPubKey.length);
        assertSame(multiSigPubKey, TradeValidation.checkMultiSigPubKey(multiSigPubKey));
    }

    @Test
    void checkMultiSigPubKeyRejectsNullPublicKey() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkMultiSigPubKey(null));
    }

    @Test
    void checkMultiSigPubKeyRejectsUncompressedPublicKey() {
        byte[] uncompressedPubKey = new ECKey().decompress().getPubKey();

        assertEquals(65, uncompressedPubKey.length);
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMultiSigPubKey(uncompressedPubKey));
    }

    @Test
    void checkMultiSigPubKeyRejectsMalformedCompressedPublicKey() {
        byte[] malformedCompressedPubKey = new byte[33];
        Arrays.fill(malformedCompressedPubKey, (byte) 0xff);
        malformedCompressedPubKey[0] = 0x02;

        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkMultiSigPubKey(malformedCompressedPubKey));
    }

    @Test
    void checkMultiSigPubKeyAcceptsValidCompressedCurvePoints() {
        // These deterministic encodings exercise x-coordinates where x^3 + 7 is a quadratic residue mod the
        // secp256k1 field prime, so both compressed y-parity prefixes map to valid curve points.
        String[] validEncodings = {
                "020000000000000000000000000000000000000000000000000000000000000001",
                "020000000000000000000000000000000000000000000000000000000000000002",
                "020000000000000000000000000000000000000000000000000000000000000003",
                "020000000000000000000000000000000000000000000000000000000000000004",
                "020000000000000000000000000000000000000000000000000000000000000006",
                "020000000000000000000000000000000000000000000000000000000000000008",
                "02000000000000000000000000000000000000000000000000000000000000000c",
                "02000000000000000000000000000000000000000000000000000000000000000d",
                "02000000000000000000000000000000000000000000000000000000000000000e",
                "030000000000000000000000000000000000000000000000000000000000000001",
                "030000000000000000000000000000000000000000000000000000000000000002",
                "030000000000000000000000000000000000000000000000000000000000000003",
                "030000000000000000000000000000000000000000000000000000000000000004",
                "030000000000000000000000000000000000000000000000000000000000000006",
                "030000000000000000000000000000000000000000000000000000000000000008",
                "03000000000000000000000000000000000000000000000000000000000000000c",
                "03000000000000000000000000000000000000000000000000000000000000000d",
                "03000000000000000000000000000000000000000000000000000000000000000e"
        };

        for (String validEncoding : validEncodings) {
            byte[] multiSigPubKey = Utilities.decodeFromHex(validEncoding);
            assertDoesNotThrow(() -> TradeValidation.checkMultiSigPubKey(multiSigPubKey), validEncoding);
        }
    }

    @Test
    void checkMultiSigPubKeyRejectsInvalidCompressedCurvePoints() {
        // These x-coordinates do not produce a quadratic residue for x^3 + 7 mod the secp256k1 field prime,
        // so neither compressed y-parity prefix can decompress to a valid curve point.
        String[] invalidEncodings = {
                "020000000000000000000000000000000000000000000000000000000000000000",
                "020000000000000000000000000000000000000000000000000000000000000005",
                "020000000000000000000000000000000000000000000000000000000000000007",
                "020000000000000000000000000000000000000000000000000000000000000009",
                "02000000000000000000000000000000000000000000000000000000000000000a",
                "02000000000000000000000000000000000000000000000000000000000000000b",
                "02000000000000000000000000000000000000000000000000000000000000000f",
                "030000000000000000000000000000000000000000000000000000000000000000",
                "030000000000000000000000000000000000000000000000000000000000000005",
                "030000000000000000000000000000000000000000000000000000000000000007",
                "030000000000000000000000000000000000000000000000000000000000000009",
                "03000000000000000000000000000000000000000000000000000000000000000a",
                "03000000000000000000000000000000000000000000000000000000000000000b",
                "03000000000000000000000000000000000000000000000000000000000000000f"
        };

        for (String invalidEncoding : invalidEncodings) {
            byte[] multiSigPubKey = Utilities.decodeFromHex(invalidEncoding);
            assertThrows(IllegalArgumentException.class,
                    () -> TradeValidation.checkMultiSigPubKey(multiSigPubKey),
                    invalidEncoding);
        }
    }

    @Test
    void checkBitcoinAddressAcceptsAddressForWalletNetwork() {
        String bitcoinAddress = SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString();

        assertSame(bitcoinAddress, TradeValidation.checkBitcoinAddress(bitcoinAddress,
                btcWalletService(MainNetParams.get())));
    }

    @Test
    void checkBitcoinAddressRejectsInvalidAddress() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBitcoinAddress("not-a-bitcoin-address",
                btcWalletService(MainNetParams.get())));
    }

    @Test
    void checkBitcoinAddressRejectsAddressFromDifferentNetwork() {
        String testnetAddress = SegwitAddress.fromKey(TestNet3Params.get(), new ECKey()).toString();

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBitcoinAddress(testnetAddress,
                btcWalletService(MainNetParams.get())));
    }

    @Test
    void checkBitcoinAddressRejectsNullAddress() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkBitcoinAddress(null,
                btcWalletService(MainNetParams.get())));
    }

    @Test
    void checkBitcoinAddressRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkBitcoinAddress(
                SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString(),
                null));
    }

    @Test
    void checkSerializedTransactionAcceptsValidSerializedTransaction() {
        byte[] serializedTransaction = serializedTransaction();

        assertSame(serializedTransaction, TradeValidation.checkSerializedTransaction(serializedTransaction,
                btcWalletService(MainNetParams.get())));
    }

    @Test
    void checkSerializedTransactionRejectsMalformedSerializedTransaction() {
        assertThrows(RuntimeException.class, () -> TradeValidation.checkSerializedTransaction(new byte[]{1, 2, 3},
                btcWalletService(MainNetParams.get())));
    }

    @Test
    void checkSerializedTransactionRejectsNullSerializedTransaction() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkSerializedTransaction(null,
                btcWalletService(MainNetParams.get())));
    }

    @Test
    void toTransactionParsesValidSerializedTransaction() {
        byte[] serializedTransaction = serializedTransaction();

        assertArrayEquals(serializedTransaction,
                TradeValidation.toTransaction(serializedTransaction, btcWalletService(MainNetParams.get()))
                        .bitcoinSerialize());
    }

    @Test
    void checkTransactionIdAcceptsValidTransactionId() {
        assertEquals(VALID_TRANSACTION_ID, TradeValidation.checkTransactionId(VALID_TRANSACTION_ID));
    }

    @Test
    void checkTransactionIdAcceptsUpperCaseTransactionId() {
        String transactionId = VALID_TRANSACTION_ID.toUpperCase(Locale.ROOT);
        assertEquals(transactionId.toLowerCase(Locale.ROOT), TradeValidation.checkTransactionId(transactionId));
    }

    @Test
    void checkTransactionIdRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTransactionId(
                VALID_TRANSACTION_ID.substring(1)));
    }

    @Test
    void checkTransactionIdRejectsNonHexTransactionId() {
        String transactionId = VALID_TRANSACTION_ID.substring(0, VALID_TRANSACTION_ID.length() - 1) + "g";

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTransactionId(transactionId));
    }

    @Test
    void checkTransactionIdRejectsNullTransactionId() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTransactionId(null));
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
        assertArrayEquals(signature, TradeValidation.toEncodedSignature(signatureBase64));
    }

    @Test
    void checkBase64SignatureRejectsInvalidBase64EncodedSignature() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkBase64Signature("not base64"));
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
        User user = userWithAcceptedMediator(mediatorNodeAddress,
                mediator(mediatorNodeAddress, mediatorPubKeyRing));

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
        User user = userWithAcceptedMediator(mediatorNodeAddress, null);

        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user));
    }

    @Test
    void getCheckedMediatorPubKeyRingRejectsMediatorWithoutPubKeyRing() {
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        User user = userWithAcceptedMediator(mediatorNodeAddress, mediator(mediatorNodeAddress, null));

        assertThrows(NullPointerException.class,
                () -> TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user));
    }

    @Test
    void checkPeersBurningManSelectionHeightAcceptsSameHeight() {
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 139, GRID_SIZE, 0));

        assertEquals(130,
                TradeValidation.checkPeersBurningManSelectionHeight(130, delayedPayoutTxReceiverService(130)));
    }

    @Test
    void checkPeersBurningManSelectionHeightAcceptsMakerOneGridAhead() {
        assertEquals(120,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 134, GRID_SIZE, 0));
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 135, GRID_SIZE, 0));

        assertEquals(120,
                TradeValidation.checkPeersBurningManSelectionHeight(120, delayedPayoutTxReceiverService(130)));
    }

    @Test
    void checkPeersBurningManSelectionHeightAcceptsTakerOneGridAhead() {
        assertEquals(120,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 134, GRID_SIZE, 0));
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 135, GRID_SIZE, 0));

        assertEquals(130,
                TradeValidation.checkPeersBurningManSelectionHeight(130, delayedPayoutTxReceiverService(120)));
    }

    @Test
    void checkPeersBurningManSelectionHeightRejectsPeerHeightZero() {
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkPeersBurningManSelectionHeight(0, delayedPayoutTxReceiverService(10)));
    }

    @Test
    void checkPeersBurningManSelectionHeightRejectsHeightsMoreThanOneGridApart() {
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkPeersBurningManSelectionHeight(120, delayedPayoutTxReceiverService(140)));
    }

    @Test
    void checkPeersBurningManSelectionHeightRejectsLocalHeightZero() {
        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkPeersBurningManSelectionHeight(10, delayedPayoutTxReceiverService(0)));
    }

    @Test
    void checkTakersRawTransactionInputsAcceptsSellerInputsForBuyOffer() {
        Coin tradeAmount = Coin.valueOf(20_000);
        Coin tradeTxFee = Coin.valueOf(300);
        Offer offer = offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = trade(offer, tradeTxFee);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = rawTransactionInputs(btcWalletService,
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
        Offer offer = offer(false, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        Trade trade = trade(offer, tradeTxFee);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = rawTransactionInputs(btcWalletService,
                offer.getBuyerSecurityDeposit().add(tradeTxFee.multiply(2)));

        assertSame(rawTransactionInputs, TradeValidation.checkTakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                trade.getOffer(),
                trade.getTradeTxFee(),
                tradeAmount));
    }

    @Test
    void checkMakersRawTransactionInputsAcceptsBuyerInputsForBuyOffer() {
        Offer offer = offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = rawTransactionInputs(btcWalletService,
                offer.getBuyerSecurityDeposit());

        assertSame(rawTransactionInputs, TradeValidation.checkMakersRawTransactionInputs(rawTransactionInputs,
                btcWalletService,
                offer));
    }

    @Test
    void checkMakersRawTransactionInputsAcceptsSellerInputsForSellOffer() {
        Offer offer = offer(false, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        List<RawTransactionInput> rawTransactionInputs = rawTransactionInputs(btcWalletService,
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
    void checkTradeTxFeeAcceptsPositiveFees() {
        Coin txFee = Coin.valueOf(300);

        assertSame(txFee, TradeValidation.checkTradeTxFee(txFee));
        assertEquals(300, TradeValidation.checkTradeTxFee(300));
    }

    @Test
    void checkTradeTxFeeAcceptsFeesWithinAllowedTolerance() {
        Coin txFee = Coin.valueOf(300);

        assertSame(txFee, TradeValidation.checkTradeTxFeeIsInTolerance(txFee, Coin.valueOf(300)));
        assertEquals(1_500, TradeValidation.checkFeeIsInTolerance(1_500, 1_000));
        assertEquals(500, TradeValidation.checkFeeIsInTolerance(500, 1_000));
    }

    @Test
    void checkTradeTxFeeRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTradeTxFee(Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTradeTxFee(0));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTradeTxFee(-1));
    }

    @Test
    void checkTradeTxFeeRejectsFeesOutsideAllowedTolerance() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkFeeIsInTolerance(2_001, 1_000));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkFeeIsInTolerance(499, 1_000));
    }

    @Test
    void checkTradeTxFeeRejectsNullFees() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTradeTxFee(null));
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTradeTxFeeIsInTolerance(null, Coin.valueOf(300)));
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTradeTxFeeIsInTolerance(Coin.valueOf(300), (Coin) null));
    }

    @Test
    void checkTradeTxFeeAcceptsCalculatedTxFee() {
        Coin txFee = TradeFeeFactory.getTradeTxFee(Coin.valueOf(2));

        assertSame(txFee, TradeValidation.checkTradeTxFeeIsInTolerance(txFee, TradeFeeFactory.getTradeTxFee(Coin.valueOf(2))));
    }

    @Test
    void checkMinerFeeRateAcceptsFeesWithinAllowedTolerance() {
        assertEquals(1_500, TradeValidation.checkMinerFeeRateIsInTolerance(1_500, 1_000));
        assertEquals(500, TradeValidation.checkMinerFeeRateIsInTolerance(500, 1_000));
    }

    @Test
    void checkMinerFeeRateRejectsFeesOutsideAllowedTolerance() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMinerFeeRateIsInTolerance(2_001, 1_000));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMinerFeeRateIsInTolerance(499, 1_000));
    }

    @Test
    void checkTakerFeeAcceptsExpectedFees() {
        Coin takerFee = Coin.valueOf(100);

        assertSame(takerFee, TradeValidation.checkTakerFee(takerFee, Coin.valueOf(100)));
        assertEquals(100, TradeValidation.checkTakerFeeInTolerance(100, 100));
        assertEquals(150, TradeValidation.checkTakerFeeInTolerance(150, 100));
        assertEquals(67, TradeValidation.checkTakerFeeInTolerance(67, 100));
    }

    @Test
    void checkTakerFeeAcceptsCalculatedExpectedFees() {
        configureTradeFeeService(Coin.valueOf(77), Coin.valueOf(100));
        Coin takerFee = Coin.valueOf(100);

        assertSame(takerFee, TradeValidation.checkTakerFee(takerFee, true, Coin.valueOf(3_000)));
        assertEquals(100, TradeValidation.checkTakerFee(100, true, Coin.valueOf(3_000)));
    }

    @Test
    void checkFeeMatchesExpectedRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTakerFee(Coin.ZERO, Coin.valueOf(100)));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTakerFee(Coin.valueOf(-1), Coin.valueOf(100)));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTakerFee(Coin.valueOf(100), Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTakerFee(Coin.valueOf(100), Coin.valueOf(-1)));
    }

    @Test
    void checkTakerFeeRejectsUnexpectedFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTakerFeeInTolerance(151, 100));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkTakerFeeInTolerance(49, 100));
    }

    @Test
    void checkTakerFeeRejectsNullFees() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTakerFee(null, Coin.valueOf(100)));
        assertThrows(NullPointerException.class, () -> TradeValidation.checkTakerFee(Coin.valueOf(100), null));
    }

    @Test
    void checkMakerFeeAcceptsExpectedFees() {
        Coin makerFee = Coin.valueOf(77);

        assertSame(makerFee, TradeValidation.checkMakerFee(makerFee, Coin.valueOf(77)));
        assertEquals(77, TradeValidation.checkMakerFeeInTolerance(77, 77));
        assertEquals(154, TradeValidation.checkMakerFeeInTolerance(154, 77));
        assertEquals(39, TradeValidation.checkMakerFeeInTolerance(39, 77));
    }

    @Test
    void checkMakerFeeAcceptsCalculatedExpectedFees() {
        configureTradeFeeService(Coin.valueOf(77), Coin.valueOf(100));
        Coin makerFee = Coin.valueOf(77);

        assertSame(makerFee, TradeValidation.checkMakerFee(makerFee, false, Coin.valueOf(3_000)));
        assertEquals(77, TradeValidation.checkMakerFee(77, false, Coin.valueOf(3_000)));
    }

    @Test
    void checkMakerFeeRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMakerFee(Coin.ZERO, Coin.valueOf(77)));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMakerFee(Coin.valueOf(-1), Coin.valueOf(77)));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMakerFee(Coin.valueOf(77), Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMakerFee(Coin.valueOf(77), Coin.valueOf(-1)));
    }

    @Test
    void checkMakerFeeRejectsUnexpectedFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMakerFee(Coin.valueOf(155), Coin.valueOf(77)));
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMakerFeeInTolerance(155, 77));
    }

    @Test
    void checkMakerFeeRejectsNullFees() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkMakerFee(null, Coin.valueOf(77)));
        assertThrows(NullPointerException.class, () -> TradeValidation.checkMakerFee(Coin.valueOf(77), null));
    }

    @Test
    void checkInputsForDepositTxRequestAcceptsValidRequest() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);

        assertSame(fixture.request, TradeValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsInvalidAccountAgeWitnessSignature() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture =
                inputsForDepositTxRequestValidationFixture(new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsTxFeeOutsideAllowedTolerance() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);
        FeeService feeService = configureTradeFeeService(Coin.valueOf(77), Coin.valueOf(100), 10);

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsUnexpectedTakerFee() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture =
                inputsForDepositTxRequestValidationFixture(null, Coin.valueOf(151));

        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    private static Offer offer(boolean isBuyOffer,
                               Coin buyerSecurityDeposit,
                               Coin sellerSecurityDeposit,
                               Coin offerAmount) {
        Offer offer = mock(Offer.class);
        when(offer.isBuyOffer()).thenReturn(isBuyOffer);
        when(offer.getBuyerSecurityDeposit()).thenReturn(buyerSecurityDeposit);
        when(offer.getSellerSecurityDeposit()).thenReturn(sellerSecurityDeposit);
        when(offer.getAmount()).thenReturn(offerAmount);
        return offer;
    }

    private static Trade trade(Offer offer, Coin tradeTxFee) {
        Trade trade = mock(Trade.class);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getTradeTxFee()).thenReturn(tradeTxFee);
        return trade;
    }

    private static BtcWalletService btcWalletService(NetworkParameters networkParameters) {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getParams()).thenReturn(networkParameters);
        return btcWalletService;
    }

    private static DelayedPayoutTxReceiverService delayedPayoutTxReceiverService(int burningManSelectionHeight) {
        DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = mock(DelayedPayoutTxReceiverService.class);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight()).thenReturn(burningManSelectionHeight);
        return delayedPayoutTxReceiverService;
    }

    private static PubKeyRing pubKeyRing(KeyPair signatureKeyPair) {
        return new PubKeyRing(signatureKeyPair.getPublic(), Encryption.generateKeyPair().getPublic());
    }

    private static User userWithAcceptedMediator(NodeAddress mediatorNodeAddress, Mediator mediator) {
        User user = mock(User.class);
        when(user.getAcceptedMediatorByAddress(mediatorNodeAddress)).thenReturn(mediator);
        return user;
    }

    private static TradeMessage tradeMessage(String tradeId) {
        TradeMessage tradeMessage = mock(TradeMessage.class);
        when(tradeMessage.getTradeId()).thenReturn(tradeId);
        return tradeMessage;
    }

    private static byte[] serializedTransaction() {
        Transaction transaction = new Transaction(MainNetParams.get());
        transaction.addInput(new TransactionInput(MainNetParams.get(),
                transaction,
                new byte[]{},
                new TransactionOutPoint(MainNetParams.get(), 0, Sha256Hash.ZERO_HASH),
                Coin.valueOf(2_000)));
        transaction.addOutput(Coin.valueOf(1_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        return transaction.bitcoinSerialize();
    }

    private static InputsForDepositTxRequestValidationFixture inputsForDepositTxRequestValidationFixture(
            byte[] accountAgeWitnessSignatureOverride) throws CryptoException {
        return inputsForDepositTxRequestValidationFixture(accountAgeWitnessSignatureOverride, Coin.valueOf(100));
    }

    private static InputsForDepositTxRequestValidationFixture inputsForDepositTxRequestValidationFixture(
            byte[] accountAgeWitnessSignatureOverride,
            Coin requestTakerFee) throws CryptoException {
        String offerId = "offer-id";
        Coin tradeAmount = Coin.valueOf(3_000);
        Coin expectedTakerFee = Coin.valueOf(100);
        FeeService feeService = configureTradeFeeService(Coin.valueOf(77), expectedTakerFee);
        Coin tradeTxFee = TradeFeeFactory.getTradeTxFee(feeService.getTxFeePerVbyte());
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        BtcWalletService btcWalletService = btcWalletService(MainNetParams.get());
        Offer offer = offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        when(offer.getId()).thenReturn(offerId);
        when(offer.getMinAmount()).thenReturn(OFFER_MIN_AMOUNT);
        when(offer.isUseMarketBasedPrice()).thenReturn(true);

        KeyPair takerSignatureKeyPair = Sig.generateKeyPair();
        PubKeyRing takerPubKeyRing = pubKeyRing(takerSignatureKeyPair);
        byte[] accountAgeWitnessSignature = accountAgeWitnessSignatureOverride != null ?
                accountAgeWitnessSignatureOverride :
                Sig.sign(takerSignatureKeyPair.getPrivate(), offerId.getBytes(StandardCharsets.UTF_8));

        List<RawTransactionInput> rawTransactionInputs = rawTransactionInputs(btcWalletService,
                offer.getSellerSecurityDeposit()
                        .add(tradeAmount)
                        .add(tradeTxFee.multiply(2)));
        InputsForDepositTxRequest request = new InputsForDepositTxRequest(offerId,
                new NodeAddress("sender.onion", 9999),
                tradeAmount.value,
                50_000_000L,
                tradeTxFee.value,
                requestTakerFee.value,
                true,
                rawTransactionInputs,
                new ECKey().getPubKey(),
                SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString(),
                takerPubKeyRing,
                "taker-account-id",
                VALID_TRANSACTION_ID,
                List.of(),
                List.of(mediatorNodeAddress),
                List.of(),
                null,
                mediatorNodeAddress,
                new NodeAddress("refund-agent.onion", 9999),
                "uid",
                1,
                accountAgeWitnessSignature,
                System.currentTimeMillis(),
                new byte[]{2},
                "SEPA",
                130);
        User user = userWithAcceptedMediator(mediatorNodeAddress,
                mediator(mediatorNodeAddress, pubKeyRing(Sig.generateKeyPair())));

        return new InputsForDepositTxRequestValidationFixture(request,
                offer,
                user,
                btcWalletService,
                mock(PriceFeedService.class),
                delayedPayoutTxReceiverService(130),
                feeService);
    }

    private static FeeService configureTradeFeeService(Coin makerFee, Coin takerFee) {
        return configureTradeFeeService(makerFee, takerFee, 1);
    }

    private static FeeService configureTradeFeeService(Coin makerFee, Coin takerFee, long txFeePerVbyte) {
        int chainHeight = GENESIS_HEIGHT;
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        FilterManager filterManager = mock(FilterManager.class);
        when(periodService.getChainHeight()).thenReturn(chainHeight);
        when(filterManager.getFilter()).thenReturn(null);
        when(daoStateService.getParamValueAsCoin(Param.MIN_MAKER_FEE_BTC, chainHeight)).thenReturn(makerFee);
        when(daoStateService.getParamValueAsCoin(Param.MIN_MAKER_FEE_BSQ, chainHeight)).thenReturn(makerFee);
        when(daoStateService.getParamValueAsCoin(Param.MIN_TAKER_FEE_BTC, chainHeight)).thenReturn(takerFee);
        when(daoStateService.getParamValueAsCoin(Param.MIN_TAKER_FEE_BSQ, chainHeight)).thenReturn(takerFee);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BTC, chainHeight)).thenReturn(Coin.SATOSHI);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BSQ, chainHeight)).thenReturn(Coin.SATOSHI);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BTC, chainHeight)).thenReturn(Coin.SATOSHI);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BSQ, chainHeight)).thenReturn(Coin.SATOSHI);
        FeeService feeService = new FeeService(daoStateService, periodService);
        feeService.onAllServicesInitialized(filterManager);
        feeService.updateFeeInfo(txFeePerVbyte, 1);
        return feeService;
    }

    private static Mediator mediator(NodeAddress mediatorNodeAddress, PubKeyRing pubKeyRing) {
        return new Mediator(mediatorNodeAddress,
                pubKeyRing,
                List.of("en"),
                System.currentTimeMillis(),
                new byte[]{1},
                "registrationSignature",
                null,
                null,
                null);
    }

    private static List<RawTransactionInput> rawTransactionInputs(BtcWalletService btcWalletService, Coin inputAmount) {
        byte[] parentTransaction = new byte[]{1, 2, 3};
        RawTransactionInput rawTransactionInput = new RawTransactionInput(0,
                parentTransaction,
                inputAmount.value);
        Transaction transaction = new Transaction(MainNetParams.get());
        transaction.addOutput(inputAmount, ScriptBuilder.createP2WPKHOutputScript(new ECKey()));

        when(btcWalletService.getTxFromSerializedTx(parentTransaction)).thenReturn(transaction);
        when(btcWalletService.isP2WH(rawTransactionInput)).thenReturn(true);

        return List.of(rawTransactionInput);
    }

    private static class InputsForDepositTxRequestValidationFixture {
        private final InputsForDepositTxRequest request;
        private final Offer offer;
        private final User user;
        private final BtcWalletService btcWalletService;
        private final PriceFeedService priceFeedService;
        private final DelayedPayoutTxReceiverService delayedPayoutTxReceiverService;
        private final FeeService feeService;

        private InputsForDepositTxRequestValidationFixture(InputsForDepositTxRequest request,
                                                           Offer offer,
                                                           User user,
                                                           BtcWalletService btcWalletService,
                                                           PriceFeedService priceFeedService,
                                                           DelayedPayoutTxReceiverService delayedPayoutTxReceiverService,
                                                           FeeService feeService) {
            this.request = request;
            this.offer = offer;
            this.user = user;
            this.btcWalletService = btcWalletService;
            this.priceFeedService = priceFeedService;
            this.delayedPayoutTxReceiverService = delayedPayoutTxReceiverService;
            this.feeService = feeService;
        }
    }
}

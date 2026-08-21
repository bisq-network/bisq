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

package bisq.core.trade.validation;

import bisq.core.btc.wallet.BtcWalletService;

import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.ValidationTestUtils.btcWalletService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TransactionValidationTest {

    /* --------------------------------------------------------------------- */
    // Bitcoin address
    /* --------------------------------------------------------------------- */

    @Test
    void checkBitcoinAddressAcceptsAddressForWalletNetwork() {
        String bitcoinAddress = SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString();

        assertSame(bitcoinAddress, TransactionValidation.checkBitcoinAddress(bitcoinAddress,
                btcWalletService()));
    }

    @Test
    void checkBitcoinAddressRejectsInvalidAddress() {
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkBitcoinAddress("not-a-bitcoin-address",
                btcWalletService()));
    }

    @Test
    void checkBitcoinAddressRejectsAddressFromDifferentNetwork() {
        String testnetAddress = SegwitAddress.fromKey(TestNet3Params.get(), new ECKey()).toString();

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkBitcoinAddress(testnetAddress,
                btcWalletService()));
    }

    @Test
    void checkBitcoinAddressRejectsNullAddress() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkBitcoinAddress(null,
                btcWalletService()));
    }

    @Test
    void checkBitcoinAddressRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkBitcoinAddress(
                SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString(),
                null));
    }

    @Test
    void checkBitcoinAddressRejectsWalletServiceWithoutParams() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkBitcoinAddress(
                SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString(),
                mock(BtcWalletService.class)));
    }

    /* --------------------------------------------------------------------- */
    // Transaction
    /* --------------------------------------------------------------------- */

    @Test
    void checkSerializedTransactionAcceptsValidSerializedTransaction() {
        byte[] serializedTransaction = ValidationTestUtils.serializedTransaction();

        assertSame(serializedTransaction, TransactionValidation.checkSerializedTransaction(serializedTransaction,
                btcWalletService()));
    }

    @Test
    void checkSerializedTransactionRejectsMalformedSerializedTransaction() {
        assertThrows(RuntimeException.class, () -> TransactionValidation.checkSerializedTransaction(new byte[]{1, 2, 3},
                btcWalletService()));
    }

    @Test
    void checkSerializedTransactionRejectsNullSerializedTransaction() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkSerializedTransaction(null,
                btcWalletService()));
    }

    @Test
    void checkTransactionAcceptsValidTransaction() {
        Transaction transaction = ValidationTestUtils.transaction(new byte[]{});

        assertSame(transaction, TransactionValidation.checkTransaction(transaction));
    }

    @Test
    void checkTransactionRejectsStructurallyInvalidTransaction() {
        Transaction transaction = ValidationTestUtils.transactionWithoutOutputs();

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkTransaction(transaction));
    }

    @Test
    void checkTransactionRejectsNullTransaction() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkTransaction(null));
    }

    @Test
    void toTransactionParsesValidSerializedVerifiedTransaction() {
        byte[] serializedTransaction = ValidationTestUtils.serializedTransaction();

        assertArrayEquals(serializedTransaction,
                TransactionValidation.toVerifiedTransaction(serializedTransaction, btcWalletService())
                        .bitcoinSerialize());
    }

    @Test
    void toVerifiedTransactionRejectsMalformedSerializedTransaction() {
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.toVerifiedTransaction(new byte[]{1, 2, 3},
                btcWalletService()));
    }

    @Test
    void toVerifiedTransactionRejectsStructurallyInvalidTransaction() {
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.toVerifiedTransaction(
                ValidationTestUtils.serializedTransactionWithoutOutputs(),
                btcWalletService()));
    }

    @Test
    void toVerifiedTransactionRejectsTransactionWithDuplicateOutpoints() {
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.toVerifiedTransaction(
                ValidationTestUtils.serializedTransactionWithDuplicateOutpoints(),
                btcWalletService()));
    }

    @Test
    void toVerifiedTransactionRejectsNullSerializedTransaction() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.toVerifiedTransaction(null,
                btcWalletService()));
    }

    @Test
    void toVerifiedTransactionRejectsNullWalletService() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.toVerifiedTransaction(
                ValidationTestUtils.serializedTransaction(),
                null));
    }

    @Test
    void toVerifiedTransactionRejectsWalletServiceWithoutParams() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.toVerifiedTransaction(
                ValidationTestUtils.serializedTransaction(),
                mock(BtcWalletService.class)));
    }

    /* --------------------------------------------------------------------- */
    // Transaction id
    /* --------------------------------------------------------------------- */

    @Test
    void checkTransactionIdAcceptsValidTransactionId() {
        assertEquals(ValidationTestUtils.VALID_TRANSACTION_ID, TransactionValidation.checkTransactionId(ValidationTestUtils.VALID_TRANSACTION_ID));
    }

    @Test
    void checkTransactionIdAcceptsUpperCaseTransactionId() {
        String transactionId = ValidationTestUtils.VALID_TRANSACTION_ID.toUpperCase(Locale.ROOT);
        assertEquals(transactionId.toLowerCase(Locale.ROOT), TransactionValidation.checkTransactionId(transactionId));
    }

    @Test
    void checkTransactionIdRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkTransactionId(
                ValidationTestUtils.VALID_TRANSACTION_ID.substring(1)));
    }

    @Test
    void checkTransactionIdRejectsNonHexTransactionId() {
        String transactionId = ValidationTestUtils.VALID_TRANSACTION_ID.substring(0, ValidationTestUtils.VALID_TRANSACTION_ID.length() - 1) + "g";

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkTransactionId(transactionId));
    }

    @Test
    void checkTransactionIdRejectsNullTransactionId() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkTransactionId(null));
    }

    /* --------------------------------------------------------------------- */
    // DER encoded ECDSA signature
    /* --------------------------------------------------------------------- */

    @Test
    void checkDerEncodedEcdsaSignatureAcceptsStrictDerEncodedCanonicalSignature() {
        byte[] bitcoinSignature = ValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ONE);

        assertSame(bitcoinSignature, TransactionValidation.checkDerEncodedEcdsaSignature(bitcoinSignature));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsNullSignature() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkDerEncodedEcdsaSignature(null));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsEmptySignature() {
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkDerEncodedEcdsaSignature(new byte[0]));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsMalformedSignature() {
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkDerEncodedEcdsaSignature(new byte[]{1, 2, 3}));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsNonStrictDerEncoding() {
        byte[] bitcoinSignature = ValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ONE);
        byte[] bitcoinSignatureWithTrailingData = Arrays.copyOf(bitcoinSignature, bitcoinSignature.length + 1);
        bitcoinSignatureWithTrailingData[bitcoinSignature.length] = 1;

        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(bitcoinSignatureWithTrailingData));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsValuesOutsideCurveOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(ValidationTestUtils.bitcoinSignature(BigInteger.ZERO, BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(ValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ZERO)));
        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(ValidationTestUtils.bitcoinSignature(ECKey.CURVE.getN(), BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(ValidationTestUtils.bitcoinSignature(BigInteger.ONE, ECKey.CURVE.getN())));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsNonCanonicalSValue() {
        byte[] bitcoinSignature = ValidationTestUtils.bitcoinSignature(BigInteger.ONE, ECKey.CURVE.getN().subtract(BigInteger.ONE));

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkDerEncodedEcdsaSignature(bitcoinSignature));
    }


    /* --------------------------------------------------------------------- */
    // Multisig public key
    /* --------------------------------------------------------------------- */

    @Test
    void checkMultiSigPubKeyAcceptsCompressedPublicKey() {
        byte[] multiSigPubKey = new ECKey().getPubKey();

        assertEquals(33, multiSigPubKey.length);
        assertSame(multiSigPubKey, TransactionValidation.checkMultiSigPubKey(multiSigPubKey));
    }

    @Test
    void checkMultiSigPubKeyRejectsNullPublicKey() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkMultiSigPubKey(null));
    }

    @Test
    void checkMultiSigPubKeyRejectsUncompressedPublicKey() {
        byte[] uncompressedPubKey = new ECKey().decompress().getPubKey();

        assertEquals(65, uncompressedPubKey.length);
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkMultiSigPubKey(uncompressedPubKey));
    }

    @Test
    void checkMultiSigPubKeyRejectsInvalidCompressedPublicKeyPrefix() {
        byte[] publicKeyWithInvalidPrefix = Arrays.copyOf(new ECKey().getPubKey(), 33);
        publicKeyWithInvalidPrefix[0] = 0x04;

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkMultiSigPubKey(publicKeyWithInvalidPrefix));
    }

    @Test
    void checkMultiSigPubKeyRejectsStandardPointAtInfinityEncoding() {
        byte[] pointAtInfinityEncoding = new byte[]{0x00};

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkMultiSigPubKey(pointAtInfinityEncoding));
    }

    @Test
    void checkMultiSigPubKeyRejectsGeneratorPoint() {
        byte[] generatorPoint = ECKey.CURVE.getG().getEncoded(true);

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkMultiSigPubKey(generatorPoint));
    }

    @Test
    void checkMultiSigPubKeyRejectsMalformedCompressedPublicKey() {
        byte[] malformedCompressedPubKey = new byte[33];
        Arrays.fill(malformedCompressedPubKey, (byte) 0xff);
        malformedCompressedPubKey[0] = 0x02;

        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkMultiSigPubKey(malformedCompressedPubKey));
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
            assertDoesNotThrow(() -> TransactionValidation.checkMultiSigPubKey(multiSigPubKey), validEncoding);
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
                    () -> TransactionValidation.checkMultiSigPubKey(multiSigPubKey),
                    invalidEncoding);
        }
    }
}

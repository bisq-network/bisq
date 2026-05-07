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

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.TradeValidationTestUtils.btcWalletService;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TransactionValidationTest {
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
    void checkSerializedTransactionAcceptsValidSerializedTransaction() {
        byte[] serializedTransaction = TradeValidationTestUtils.serializedTransaction();

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
        Transaction transaction = TradeValidationTestUtils.transaction(new byte[]{});

        assertSame(transaction, TransactionValidation.checkTransaction(transaction));
    }

    @Test
    void checkTransactionRejectsStructurallyInvalidTransaction() {
        Transaction transaction = TradeValidationTestUtils.transactionWithoutOutputs();

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkTransaction(transaction));
    }

    @Test
    void checkTransactionRejectsNullTransaction() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkTransaction(null));
    }

    @Test
    void toTransactionParsesValidSerializedVerifiedTransaction() {
        byte[] serializedTransaction = TradeValidationTestUtils.serializedTransaction();

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
                TradeValidationTestUtils.serializedTransactionWithoutOutputs(),
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
                TradeValidationTestUtils.serializedTransaction(),
                null));
    }


    @Test
    void checkTransactionIdAcceptsValidTransactionId() {
        assertEquals(TradeValidationTestUtils.VALID_TRANSACTION_ID, TransactionValidation.checkTransactionId(TradeValidationTestUtils.VALID_TRANSACTION_ID));
    }

    @Test
    void checkTransactionIdAcceptsUpperCaseTransactionId() {
        String transactionId = TradeValidationTestUtils.VALID_TRANSACTION_ID.toUpperCase(Locale.ROOT);
        assertEquals(transactionId.toLowerCase(Locale.ROOT), TransactionValidation.checkTransactionId(transactionId));
    }

    @Test
    void checkTransactionIdRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkTransactionId(
                TradeValidationTestUtils.VALID_TRANSACTION_ID.substring(1)));
    }

    @Test
    void checkTransactionIdRejectsNonHexTransactionId() {
        String transactionId = TradeValidationTestUtils.VALID_TRANSACTION_ID.substring(0, TradeValidationTestUtils.VALID_TRANSACTION_ID.length() - 1) + "g";

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkTransactionId(transactionId));
    }

    @Test
    void checkTransactionIdRejectsNullTransactionId() {
        assertThrows(NullPointerException.class, () -> TransactionValidation.checkTransactionId(null));
    }

    @Test
    void checkDerEncodedEcdsaSignatureAcceptsStrictDerEncodedCanonicalSignature() {
        byte[] bitcoinSignature = TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ONE);

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
        byte[] bitcoinSignature = TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ONE);
        byte[] bitcoinSignatureWithTrailingData = Arrays.copyOf(bitcoinSignature, bitcoinSignature.length + 1);
        bitcoinSignatureWithTrailingData[bitcoinSignature.length] = 1;

        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(bitcoinSignatureWithTrailingData));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsValuesOutsideCurveOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(TradeValidationTestUtils.bitcoinSignature(BigInteger.ZERO, BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, BigInteger.ZERO)));
        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(TradeValidationTestUtils.bitcoinSignature(ECKey.CURVE.getN(), BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> TransactionValidation.checkDerEncodedEcdsaSignature(TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, ECKey.CURVE.getN())));
    }

    @Test
    void checkDerEncodedEcdsaSignatureRejectsNonCanonicalSValue() {
        byte[] bitcoinSignature = TradeValidationTestUtils.bitcoinSignature(BigInteger.ONE, ECKey.CURVE.getN().subtract(BigInteger.ONE));

        assertThrows(IllegalArgumentException.class, () -> TransactionValidation.checkDerEncodedEcdsaSignature(bitcoinSignature));
    }

}

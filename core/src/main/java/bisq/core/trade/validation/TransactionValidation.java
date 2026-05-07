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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.VerificationException;

import com.google.common.annotations.VisibleForTesting;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.Locale;

import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class TransactionValidation {
    private TransactionValidation() {
    }

    /* --------------------------------------------------------------------- */
    // Bitcoin address
    /* --------------------------------------------------------------------- */

    public static String checkBitcoinAddress(String bitcoinAddress, BtcWalletService btcWalletService) {
        checkNonBlankString(bitcoinAddress, "bitcoinAddress");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        try {
            Address.fromString(btcWalletService.getParams(), bitcoinAddress).getOutputScriptType();
            return bitcoinAddress;
        } catch (AddressFormatException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid bitcoin address: " + bitcoinAddress, e);
        }
    }


    /* --------------------------------------------------------------------- */
    // Transaction ID
    /* --------------------------------------------------------------------- */

    public static String checkTransactionId(String txId) {
        checkNonBlankString(txId, "txId");

        try {
            return Sha256Hash.wrap(txId.toLowerCase(Locale.ROOT)).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction ID: " + txId, e);
        }
    }


    /* --------------------------------------------------------------------- */
    // Transaction structure
    /* --------------------------------------------------------------------- */

    public static Transaction checkTransaction(Transaction transaction) {
        checkNotNull(transaction, "transaction must not be null");
        try {
            transaction.verify();
        } catch (VerificationException e) {
            throw new IllegalArgumentException("Invalid transaction", e);
        }
        return transaction;
    }

    public static byte[] checkSerializedTransaction(byte[] serializedTransaction,
                                                    BtcWalletService btcWalletService) {
        checkNonEmptyBytes(serializedTransaction, "serializedTransaction");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        toVerifiedTransaction(serializedTransaction, btcWalletService);
        return serializedTransaction;
    }

    public static Transaction toVerifiedTransaction(byte[] serializedTransaction,
                                                    BtcWalletService btcWalletService) {
        checkNonEmptyBytes(serializedTransaction, "serializedTransaction");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        try {
            Transaction transaction = new Transaction(btcWalletService.getParams(), serializedTransaction);
            transaction.verify();
            return transaction;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid serialized transaction", e);
        }
    }


    /* --------------------------------------------------------------------- */
    // Transaction signature data
    /* --------------------------------------------------------------------- */

    @VisibleForTesting
    static boolean hasSignatureData(TransactionInput input) {
        return input.getScriptBytes().length > 0 || input.hasWitness();
    }


    /* --------------------------------------------------------------------- */
    // Bitcoin signature
    /* --------------------------------------------------------------------- */

    public static byte[] checkDerEncodedEcdsaSignature(byte[] bitcoinSignature) {
        checkNonEmptyBytes(bitcoinSignature, "bitcoinSignature");
        try {
            ECKey.ECDSASignature signature = ECKey.ECDSASignature.decodeFromDER(bitcoinSignature);
            checkArgument(Arrays.equals(bitcoinSignature, signature.encodeToDER()),
                    "bitcoinSignature must be strictly DER encoded");
            checkArgument(isValidSignatureValue(signature.r),
                    "bitcoinSignature r value is outside of allowed range");
            checkArgument(isValidSignatureValue(signature.s),
                    "bitcoinSignature s value is outside of allowed range");
            checkArgument(signature.isCanonical(),
                    "bitcoinSignature must use low-S canonical encoding");
            return bitcoinSignature;
        } catch (SignatureDecodeException e) {
            throw new IllegalArgumentException("Invalid bitcoin signature", e);
        }
    }

    @VisibleForTesting
    static boolean isValidSignatureValue(BigInteger value) {
        return value.signum() > 0 && value.compareTo(ECKey.CURVE.getN()) < 0;
    }


    /* --------------------------------------------------------------------- */
    // Multisig public key
    /* --------------------------------------------------------------------- */

    public static byte[] checkMultiSigPubKey(byte[] multiSigPubKey) {
        checkNonEmptyBytes(multiSigPubKey, "multiSigPubKey");
        checkArgument(multiSigPubKey.length == 33, "multiSigPubKey must be compressed");

        // Check that the multisig key decompresses to a valid curve point:
        ECKey.fromPublicOnly(multiSigPubKey);
        return multiSigPubKey;
    }
}

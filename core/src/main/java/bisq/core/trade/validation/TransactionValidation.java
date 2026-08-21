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
import org.bitcoinj.core.NetworkParameters;
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
    private static final byte[] SECP256K1_GENERATOR_PUB_KEY = ECKey.CURVE.getG().getEncoded(true);

    private TransactionValidation() {
    }

    /* --------------------------------------------------------------------- */
    // Bitcoin address
    /* --------------------------------------------------------------------- */

    public static String checkBitcoinAddress(String bitcoinAddress, BtcWalletService btcWalletService) {
        checkNonBlankString(bitcoinAddress, "bitcoinAddress");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        NetworkParameters params = checkNotNull(btcWalletService.getParams(),
                "btcWalletService.getParams() must not be null");

        try {
            Address.fromString(params, bitcoinAddress).getOutputScriptType();
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
        NetworkParameters params = checkNotNull(btcWalletService.getParams(),
                "btcWalletService.getParams() must not be null");

        try {
            Transaction transaction = new Transaction(params, serializedTransaction);
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

    // Expects raw DER-encoded ECDSA signature without a trailing sighash byte
    // (e.g. the output of ECKey.ECDSASignature.encodeToDER()). Signatures extracted
    // from a scriptSig or witness stack include a sighash byte and must have it
    // stripped before being passed here.
    public static byte[] checkDerEncodedEcdsaSignature(byte[] bitcoinSignature) {
        toVerifiedDerEncodedEcdsaSignature(bitcoinSignature);
        return bitcoinSignature;
    }

    public static ECKey.ECDSASignature toVerifiedDerEncodedEcdsaSignature(byte[] bitcoinSignature) {
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
            return signature;
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
        checkArgument(multiSigPubKey[0] == 0x02 || multiSigPubKey[0] == 0x03,
                "multiSigPubKey must use a valid compressed public key prefix");
        checkArgument(!Arrays.equals(multiSigPubKey, SECP256K1_GENERATOR_PUB_KEY),
                "multiSigPubKey must not be the secp256k1 generator point");

        // Check that the multisig key decompresses to a valid curve point:
        ECKey key = ECKey.fromPublicOnly(multiSigPubKey);
        checkArgument(key.isCompressed(), "multiSigPubKey must be compressed");
        checkArgument(!key.getPubKeyPoint().isInfinity(), "multiSigPubKey must not be point at infinity");
        return multiSigPubKey;
    }
}

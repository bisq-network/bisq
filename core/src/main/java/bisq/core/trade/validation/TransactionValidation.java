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

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletUtils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;

import com.google.common.annotations.VisibleForTesting;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.List;
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
    // Transaction inputs
    /* --------------------------------------------------------------------- */

    public static void checkInputOutpoints(Transaction transaction,
                                           int startIndex,
                                           List<RawTransactionInput> expectedInputs,
                                           NetworkParameters params,
                                           String inputOwner) {
        for (int i = 0; i < expectedInputs.size(); i++) {
            RawTransactionInput expectedInput = checkNotNull(expectedInputs.get(i),
                    "%s input at position %s must not be null",
                    inputOwner,
                    i);
            TransactionOutPoint expectedOutpoint = WalletUtils.getConnectedOutPoint(expectedInput, params);
            TransactionOutPoint actualOutpoint = transaction.getInput(startIndex + i).getOutpoint();
            checkArgument(actualOutpoint.getIndex() == expectedOutpoint.getIndex() &&
                            actualOutpoint.getHash().equals(expectedOutpoint.getHash()),
                    "Transaction input %s does not match expected %s input %s",
                    startIndex + i,
                    inputOwner,
                    i);
        }
    }

    // Bisq wallets sign only P2PK, P2PKH and P2WPKH inputs, always with SIGHASH_ALL and in one fixed form (see
    // TradeWalletService.signInput). We accept only this form. Script.correctlySpends verifies signatures only for
    // these types, and even for them it does not check all rules of the network, for example the witness structure,
    // the ANYONECANPAY flag of a witness signature or the relay rules for the scriptSig.
    public static void checkInputSignatures(Transaction transaction,
                                            int startIndex,
                                            List<RawTransactionInput> expectedInputs,
                                            NetworkParameters params,
                                            String inputOwner) {
        checkInputOutpoints(transaction, startIndex, expectedInputs, params, inputOwner);
        for (int i = 0; i < expectedInputs.size(); i++) {
            int inputIndex = startIndex + i;
            TransactionInput input = transaction.getInput(inputIndex);
            TransactionOutput spentOutput = checkNotNull(
                    WalletUtils.getConnectedOutPoint(expectedInputs.get(i), params).getConnectedOutput(),
                    "%s input at position %s has no connected output",
                    inputOwner,
                    i);
            Script scriptPubKey = spentOutput.getScriptPubKey();
            checkArgument(isSignedInBisqWalletForm(input, scriptPubKey),
                    "Transaction input %s is not a P2PK, P2PKH or P2WPKH spend in the form of a Bisq wallet " +
                            "for expected %s input %s",
                    inputIndex,
                    inputOwner,
                    i);
            try {
                input.getScriptSig().correctlySpends(transaction,
                        inputIndex,
                        input.getWitness(),
                        spentOutput.getValue(),
                        scriptPubKey,
                        Script.ALL_VERIFY_FLAGS);
            } catch (ScriptException e) {
                throw new IllegalArgumentException(String.format(
                        "Transaction input %s has no valid signature for expected %s input %s",
                        inputIndex,
                        inputOwner,
                        i), e);
            }
        }
    }

    // P2WPKH: empty scriptSig and a witness with the signature and the compressed public key.
    // P2PKH: the signature and the public key as shortest pushes in the scriptSig, no witness.
    // P2PK: the signature as shortest push in the scriptSig, no witness.
    // The public key must have a standard encoding, the network does not relay a hybrid encoded key.
    private static boolean isSignedInBisqWalletForm(TransactionInput input, Script scriptPubKey) {
        TransactionWitness witness = input.getWitness();
        if (ScriptPattern.isP2WPKH(scriptPubKey)) {
            return input.getScriptBytes().length == 0 &&
                    witness.getPushCount() == 2 &&
                    isSignedWithSigHashAll(witness.getPush(0)) &&
                    witness.getPush(1).length == 33;
        }

        int numPushes;
        if (ScriptPattern.isP2PKH(scriptPubKey)) {
            numPushes = 2;
        } else if (ScriptPattern.isP2PK(scriptPubKey)) {
            numPushes = 1;
        } else {
            return false;
        }
        if (input.hasWitness()) {
            return false;
        }
        List<ScriptChunk> chunks = input.getScriptSig().getChunks();
        if (chunks.size() != numPushes ||
                !chunks.stream().allMatch(chunk -> chunk.data != null && chunk.isShortestPossiblePushData()) ||
                !isSignedWithSigHashAll(chunks.get(0).data)) {
            return false;
        }
        byte[] pubKey = numPushes == 2 ? chunks.get(1).data : ScriptPattern.extractKeyFromP2PK(scriptPubKey);
        return ECKey.isPubKeyCanonical(pubKey);
    }

    private static boolean isSignedWithSigHashAll(byte[] signature) {
        return signature.length > 0 &&
                (signature[signature.length - 1] & 0xff) == Transaction.SigHash.ALL.value;
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

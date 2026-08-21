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

package bisq.core.btc.wallet.validation;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bouncycastle.math.ec.ECPoint;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class WitnessValidation {
    private static final int P2WPKH_WITNESS_PUSH_COUNT = 2;
    private static final int MAX_DER_SIGNATURE_WITH_SIGHASH_LENGTH = 73;
    private static final int COMPRESSED_PUB_KEY_LENGTH = 33;

    private WitnessValidation() {
    }

    public static TransactionWitness checkCanonicalP2WpkhWitness(TransactionWitness witness, int inputIndex) {
        TransactionWitness checkedWitness = checkNotNull(witness,
                "witness must not be null for input %s", inputIndex);
        checkArgument(!TransactionWitness.EMPTY.equals(checkedWitness),
                "witness must not be empty for input %s", inputIndex);
        checkArgument(checkedWitness.getPushCount() == P2WPKH_WITNESS_PUSH_COUNT,
                "P2WPKH witness for input %s must have exactly %s pushes. pushCount=%s",
                inputIndex,
                P2WPKH_WITNESS_PUSH_COUNT,
                checkedWitness.getPushCount());

        byte[] signature = checkNotNull(checkedWitness.getPush(0),
                "witness signature must not be null for input %s", inputIndex);
        checkArgument(signature.length <= MAX_DER_SIGNATURE_WITH_SIGHASH_LENGTH,
                "witness signature for input %s exceeds maximum size. size=%s, max=%s",
                inputIndex,
                signature.length,
                MAX_DER_SIGNATURE_WITH_SIGHASH_LENGTH);

        try {
            TransactionSignature transactionSignature = TransactionSignature.decodeFromBitcoin(signature,
                    true,
                    true);
            checkArgument(transactionSignature.sigHashMode() == Transaction.SigHash.ALL &&
                            !transactionSignature.anyoneCanPay(),
                    "witness signature for input %s must use SIGHASH_ALL without ANYONECANPAY",
                    inputIndex);
        } catch (SignatureDecodeException | VerificationException e) {
            throw new IllegalArgumentException("Invalid canonical witness signature for input " + inputIndex, e);
        }

        byte[] pubKey = checkNotNull(checkedWitness.getPush(1),
                "witness pubKey must not be null for input %s", inputIndex);
        checkArgument(pubKey.length == COMPRESSED_PUB_KEY_LENGTH,
                "witness pubKey for input %s must be compressed. size=%s",
                inputIndex,
                pubKey.length);
        checkArgument(pubKey[0] == 0x02 || pubKey[0] == 0x03,
                "witness pubKey for input %s must use a valid compressed public key prefix",
                inputIndex);
        try {
            ECKey key = ECKey.fromPublicOnly(pubKey);
            ECPoint point = key.getPubKeyPoint();
            checkArgument(key.isCompressed() && !point.isInfinity() && point.isValid(),
                    "witness pubKey for input %s must be a compressed valid curve point",
                    inputIndex);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("witness pubKey for input " + inputIndex +
                    " must be a compressed valid curve point", e);
        }

        return checkedWitness;
    }
}

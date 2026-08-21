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
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;

import java.math.BigInteger;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WitnessValidationTest {
    @Test
    void checkCanonicalP2WpkhWitnessAcceptsCanonicalWitness() {
        TransactionWitness witness = canonicalP2wpkhWitness();

        assertSame(witness, WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsEmptyWitness() {
        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(TransactionWitness.EMPTY, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsUnexpectedPushCount() {
        TransactionWitness witness = new TransactionWitness(3);
        witness.setPush(0, canonicalSignature(Transaction.SigHash.ALL, false));
        witness.setPush(1, new ECKey().getPubKey());
        witness.setPush(2, new byte[]{});

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsOversizedSignaturePush() {
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, new byte[74]);
        witness.setPush(1, new ECKey().getPubKey());

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsMalformedSignatureEncoding() {
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, new byte[]{0x30, 0x01, 0x01, Transaction.SigHash.ALL.byteValue()});
        witness.setPush(1, new ECKey().getPubKey());

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsNonCanonicalHighSSignature() {
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, bitcoinSignature(BigInteger.ONE, ECKey.CURVE.getN().subtract(BigInteger.ONE)));
        witness.setPush(1, new ECKey().getPubKey());

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsNonAllSignatureHashMode() {
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, canonicalSignature(Transaction.SigHash.SINGLE, false));
        witness.setPush(1, new ECKey().getPubKey());

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsAnyoneCanPaySignature() {
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, canonicalSignature(Transaction.SigHash.ALL, true));
        witness.setPush(1, new ECKey().getPubKey());

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsUncompressedPubKey() {
        ECKey key = new ECKey();
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, canonicalSignature(Transaction.SigHash.ALL, false));
        witness.setPush(1, key.decompress().getPubKey());

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsInvalidCompressedPubKeyPrefix() {
        byte[] invalidCompressedPubKey = Arrays.copyOf(new ECKey().getPubKey(), 33);
        invalidCompressedPubKey[0] = 0x04;

        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, canonicalSignature(Transaction.SigHash.ALL, false));
        witness.setPush(1, invalidCompressedPubKey);

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    @Test
    void checkCanonicalP2WpkhWitnessRejectsInvalidCompressedPubKey() {
        byte[] invalidCompressedPubKey = new byte[33];
        Arrays.fill(invalidCompressedPubKey, (byte) 0xff);
        invalidCompressedPubKey[0] = 0x02;

        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, canonicalSignature(Transaction.SigHash.ALL, false));
        witness.setPush(1, invalidCompressedPubKey);

        assertThrows(IllegalArgumentException.class,
                () -> WitnessValidation.checkCanonicalP2WpkhWitness(witness, 0));
    }

    private static TransactionWitness canonicalP2wpkhWitness() {
        ECKey key = new ECKey();
        return TransactionWitness.redeemP2WPKH(canonicalTransactionSignature(Transaction.SigHash.ALL, false), key);
    }

    private static byte[] canonicalSignature(Transaction.SigHash sigHash, boolean anyoneCanPay) {
        return canonicalTransactionSignature(sigHash, anyoneCanPay).encodeToBitcoin();
    }

    private static TransactionSignature canonicalTransactionSignature(Transaction.SigHash sigHash,
                                                                      boolean anyoneCanPay) {
        ECKey key = new ECKey();
        ECKey.ECDSASignature signature = key.sign(Sha256Hash.ZERO_HASH).toCanonicalised();
        return new TransactionSignature(signature, sigHash, anyoneCanPay);
    }

    private static byte[] bitcoinSignature(BigInteger r, BigInteger s) {
        byte[] derSignature = new ECKey.ECDSASignature(r, s).encodeToDER();
        byte[] bitcoinSignature = Arrays.copyOf(derSignature, derSignature.length + 1);
        bitcoinSignature[derSignature.length] = Transaction.SigHash.ALL.byteValue();
        return bitcoinSignature;
    }
}

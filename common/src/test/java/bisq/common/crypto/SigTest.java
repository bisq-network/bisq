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

package bisq.common.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAPublicKeySpec;

import java.math.BigInteger;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SigTest {
    private static final byte[] DATA = "data".getBytes();

    @Test
    public void acceptsKeysWeGenerateOurselves() throws Exception {
        KeyPair keyPair = Sig.generateKeyPair();
        byte[] encoded = Sig.getPublicKeyBytes(keyPair.getPublic());

        assertTrue(encoded.length <= Sig.MAX_SIG_PUB_KEY_BYTES,
                "Our own keys must fit into the accepted size, but were " + encoded.length + " bytes");
        PublicKey publicKey = Sig.getPublicKeyFromBytes(encoded);
        assertEquals(encoded.length, Sig.getPublicKeyBytes(publicKey).length);
        assertTrue(Sig.verify(publicKey, DATA, Sig.sign(keyPair.getPrivate(), DATA)));
    }

    // Nodes up to v1.10.11 accept a key of any size. Such a key can be in an entry which an honest peer relays and
    // in a trade or dispute which we have stored, so we must still read it. We refuse it when we verify with it.
    @Test
    public void readsOversizedEncodingButDoesNotVerifyWithIt() throws Exception {
        BigInteger p = BigInteger.ONE.shiftLeft(2047).setBit(0);
        BigInteger q = BigInteger.ONE.shiftLeft(159).nextProbablePrime();
        PublicKey key = KeyFactory.getInstance(Sig.KEY_ALGO)
                .generatePublic(new DSAPublicKeySpec(p.subtract(BigInteger.TWO), p, q, p.subtract(BigInteger.ONE)));
        byte[] encoded = key.getEncoded();
        assertTrue(encoded.length > Sig.MAX_SIG_PUB_KEY_BYTES,
                "Test premise: the encoding must be above the size limit, but was " + encoded.length + " bytes");

        PublicKey publicKey = Sig.getPublicKeyFromBytes(encoded);

        assertArrayEquals(encoded, Sig.getPublicKeyBytes(publicKey));
        CryptoException e = assertThrows(CryptoException.class, () -> Sig.verify(publicKey, DATA, new byte[46]));
        assertTrue(e.getMessage().contains("exceeds the permitted size"), e.getMessage());
    }

    // A hand crafted SPKI can carry a large modulus in few bytes by pairing it with a tiny generator and public
    // value, so the encoded size alone does not bound the cost of a verification. With a public value of 1 anyone
    // can create signatures which the JDK accepts, so the key works as an identity on nodes without the bound.
    @Test
    public void readsSmallEncodingCarryingAnOversizedModulusButDoesNotVerifyWithIt() throws Exception {
        SecureRandom random = new SecureRandom();
        BigInteger p = new BigInteger(3072, random).setBit(3071).setBit(0);
        BigInteger q = BigInteger.probablePrime(160, random);
        byte[] encoded = KeyFactory.getInstance(Sig.KEY_ALGO)
                .generatePublic(new DSAPublicKeySpec(BigInteger.ONE, p, q, BigInteger.TWO)).getEncoded();
        assertTrue(encoded.length <= Sig.MAX_SIG_PUB_KEY_BYTES,
                "Test premise: the oversized modulus must pass the size check, but the encoding was "
                        + encoded.length + " bytes");

        PublicKey publicKey = Sig.getPublicKeyFromBytes(encoded);

        assertEquals(3072, ((DSAPublicKey) publicKey).getParams().getP().bitLength());
        byte[] signature = signatureForPublicValueOne(p, q, DATA, random);
        Signature jdkSignature = Signature.getInstance("SHA256withDSA");
        jdkSignature.initVerify(publicKey);
        jdkSignature.update(DATA);
        assertTrue(jdkSignature.verify(signature), "Test premise: the JDK accepts the signature");
        CryptoException e = assertThrows(CryptoException.class, () -> Sig.verify(publicKey, DATA, signature));
        assertTrue(e.getMessage().contains("modulus exceeds the permitted size"), e.getMessage());
    }

    // The JDK reads a key with such parameters and fails with an ArithmeticException when it verifies with it
    @Test
    public void doesNotVerifyWithAModulusWhichIsNotPositive() throws Exception {
        BigInteger q = BigInteger.ONE.shiftLeft(159).nextProbablePrime();
        for (BigInteger p : new BigInteger[]{BigInteger.ZERO, BigInteger.valueOf(-7)}) {
            PublicKey publicKey = Sig.getPublicKeyFromBytes(KeyFactory.getInstance(Sig.KEY_ALGO)
                    .generatePublic(new DSAPublicKeySpec(BigInteger.ONE, p, q, BigInteger.TWO)).getEncoded());

            CryptoException e = assertThrows(CryptoException.class,
                    () -> Sig.verify(publicKey, DATA, derSequence(BigInteger.ONE, BigInteger.ONE)));
            assertTrue(e.getMessage().contains("modulus is not positive"), e.getMessage());
        }
    }

    // With an even q an even signature value has no inverse, which the JDK reports as an ArithmeticException
    @Test
    public void reportsAFailedComputationOfTheVerificationAsCryptoException() throws Exception {
        BigInteger p = BigInteger.ONE.shiftLeft(1023).setBit(0);
        BigInteger q = BigInteger.ONE.shiftLeft(159);
        PublicKey publicKey = Sig.getPublicKeyFromBytes(KeyFactory.getInstance(Sig.KEY_ALGO)
                .generatePublic(new DSAPublicKeySpec(BigInteger.ONE, p, q, BigInteger.TWO)).getEncoded());
        byte[] signature = derSequence(BigInteger.ONE, BigInteger.TWO);
        Signature jdkSignature = Signature.getInstance("SHA256withDSA");
        jdkSignature.initVerify(publicKey);
        jdkSignature.update(DATA);
        assertThrows(ArithmeticException.class, () -> jdkSignature.verify(signature), "Test premise");

        CryptoException e = assertThrows(CryptoException.class, () -> Sig.verify(publicKey, DATA, signature));
        assertTrue(e.getCause() instanceof ArithmeticException, String.valueOf(e.getCause()));
    }

    @Test
    public void rejectsMalformedEncoding() {
        assertThrows(KeyConversionException.class, () -> Sig.getPublicKeyFromBytes(new byte[]{1, 2, 3}));
    }

    // For g = 2 and y = 1 the verification computes (2^(z/s) mod p) mod q, with z from the hash of the data, and
    // compares it with r. So r = (2^e mod p) mod q and s = z/e are a signature, for any e.
    private static byte[] signatureForPublicValueOne(BigInteger p, BigInteger q, byte[] data, SecureRandom random)
            throws Exception {
        byte[] hash = Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(data), q.bitLength() / 8);
        BigInteger z = new BigInteger(1, hash);
        BigInteger r;
        BigInteger s;
        do {
            BigInteger e = new BigInteger(q.bitLength() - 1, random).add(BigInteger.ONE);
            r = BigInteger.TWO.modPow(e, p).mod(q);
            s = z.multiply(e.modInverse(q)).mod(q);
        } while (r.signum() == 0 || s.signum() == 0);
        return derSequence(r, s);
    }

    private static byte[] derSequence(BigInteger r, BigInteger s) {
        byte[] rBytes = r.toByteArray();
        byte[] sBytes = s.toByteArray();
        byte[] der = new byte[6 + rBytes.length + sBytes.length];
        der[0] = 0x30;
        der[1] = (byte) (4 + rBytes.length + sBytes.length);
        der[2] = 0x02;
        der[3] = (byte) rBytes.length;
        System.arraycopy(rBytes, 0, der, 4, rBytes.length);
        der[4 + rBytes.length] = 0x02;
        der[5 + rBytes.length] = (byte) sBytes.length;
        System.arraycopy(sBytes, 0, der, 6 + rBytes.length, sBytes.length);
        return der;
    }
}

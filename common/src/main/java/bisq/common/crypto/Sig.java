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

import bisq.common.util.Base64;
import bisq.common.util.Utilities;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StorageSignatureKeyPair/STORAGE_SIGN_KEY_ALGO: That is used for signing the data to be stored to the P2P network (by flooding).
 * The algo is selected because it originated from the TomP2P version which used DSA.
 * Changing to EC keys might be considered.
 * <p></p>
 * MsgSignatureKeyPair/MSG_SIGN_KEY_ALGO/MSG_SIGN_ALGO: That is used when sending a message to a peer which is encrypted and signed.
 * Changing to EC keys might be considered.
 */
public class Sig {
    private static final Logger log = LoggerFactory.getLogger(Sig.class);

    public static final String KEY_ALGO = "DSA";
    private static final String ALGO = "SHA256withDSA";

    // We generate 1024 bit DSA keys only, which encode to 441 to 444 bytes as X.509 SubjectPublicKeyInfo.
    // The bound is the same one WitnessOwnershipProof already applies to owner public keys, so a key which
    // passes one check passes the other.
    public static final int MAX_SIG_PUB_KEY_BYTES = 600;

    // The encoded size alone does not bound the modulus: a hand crafted SPKI can carry a huge p together with a
    // tiny g and y. DSA verification is quadratic in the modulus size, so we bound p explicitly as well.
    private static final int MAX_MODULUS_BIT_LENGTH = 2048;


    /**
     * @return keyPair
     */
    public static KeyPair generateKeyPair() {
        long ts = System.currentTimeMillis();
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGO);
            keyPairGenerator.initialize(1024);
            return keyPairGenerator.genKeyPair();
        } catch (NoSuchAlgorithmException e) {
            log.error("Could not create key.", e);
            throw new RuntimeException("Could not create key.");
        }
    }


    /**
     * @param privateKey
     * @param data
     * @return
     */
    public static byte[] sign(PrivateKey privateKey, byte[] data) throws CryptoException {
        try {
            Signature sig = Signature.getInstance(ALGO);
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new CryptoException("Signing failed. " + e.getMessage());
        }
    }

    /**
     * @param privateKey
     * @param message    UTF-8 encoded message to sign
     * @return Base64 encoded signature
     */
    public static String sign(PrivateKey privateKey, String message) throws CryptoException {
        byte[] sigAsBytes = sign(privateKey, message.getBytes(StandardCharsets.UTF_8));
        return Base64.encode(sigAsBytes);
    }

    /**
     * @param publicKey
     * @param data
     * @param signature
     * @return
     */
    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) throws CryptoException {
        checkKeyForVerification(publicKey);
        try {
            Signature sig = Signature.getInstance(ALGO);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new CryptoException("Signature verification failed", e);
        }
    }

    /**
     * @param publicKey
     * @param message   UTF-8 encoded message
     * @param signature Base64 encoded signature
     * @return
     */
    public static boolean verify(PublicKey publicKey, String message, String signature) throws CryptoException {
        return verify(publicKey, message.getBytes(StandardCharsets.UTF_8), Base64.decode(signature));
    }

    /**
     * @param sigPublicKeyBytes
     * @return
     */
    public static PublicKey getPublicKeyFromBytes(byte[] sigPublicKeyBytes) {
        try {
            return KeyFactory.getInstance(Sig.KEY_ALGO).generatePublic(new X509EncodedKeySpec(sigPublicKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            // The key is peer controlled and has no size limit here, so we log only its start
            byte[] start = Arrays.copyOf(sigPublicKeyBytes, Math.min(sigPublicKeyBytes.length, MAX_SIG_PUB_KEY_BYTES));
            log.error("Error creating sigPublicKey from bytes. length={}, first bytes as hex={}, error={}",
                    sigPublicKeyBytes.length, Utilities.bytesAsHexString(start), e);
            e.printStackTrace();
            throw new KeyConversionException(e);
        }
    }

    public static byte[] getPublicKeyBytes(PublicKey sigPublicKey) {
        return new X509EncodedKeySpec(sigPublicKey.getEncoded()).getEncoded();
    }

    // The key is peer controlled, and the JDK does not bound the DSA modulus, so a peer could impose an arbitrarily
    // expensive verification on us. We refuse such a key here and not where we read it. Nodes up to v1.10.11
    // accept it, so it can be in an entry which an honest peer relays and in a trade or dispute which we have
    // stored. A key which we cannot read would close the connection to that peer and make the whole stored file
    // unreadable.
    private static void checkKeyForVerification(PublicKey publicKey) throws CryptoException {
        if (!(publicKey instanceof DSAPublicKey dsaKey) || dsaKey.getParams() == null) {
            throw new CryptoException("Sig public key is not a DSA key with parameters");
        }
        int modulusBitLength = dsaKey.getParams().getP().bitLength();
        if (modulusBitLength > MAX_MODULUS_BIT_LENGTH) {
            throw new CryptoException("Sig public key modulus exceeds the permitted size. bitLength=" +
                    modulusBitLength + ", max=" + MAX_MODULUS_BIT_LENGTH);
        }
        byte[] encoded = publicKey.getEncoded();
        if (encoded == null || encoded.length > MAX_SIG_PUB_KEY_BYTES) {
            throw new CryptoException("Sig public key exceeds the permitted size. length=" +
                    (encoded == null ? null : encoded.length) + ", max=" + MAX_SIG_PUB_KEY_BYTES);
        }
    }
}

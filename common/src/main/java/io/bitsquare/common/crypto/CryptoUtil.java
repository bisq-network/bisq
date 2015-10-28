/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.common.crypto;

import io.bitsquare.common.util.Utilities;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class CryptoUtil {
    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);
    public static final String STORAGE_SIGN_KEY_ALGO = "DSA";
    public static final String MSG_SIGN_KEY_ALGO = "DSA";
    public static final String MSG_ENCR_KEY_ALGO = "RSA";

    public static final String SYM_ENCR_KEY_ALGO = "AES";
    public static final String SYM_CIPHER = "AES";
    public static final String ASYM_CIPHER = "RSA"; //RSA/ECB/PKCS1Padding
    public static final String MSG_SIGN_ALGO = "SHA1withDSA";


    public static KeyPair generateStorageSignatureKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(STORAGE_SIGN_KEY_ALGO);
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.trace("Generate storageSignatureKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public static KeyPair generateMsgSignatureKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(MSG_SIGN_KEY_ALGO);
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.trace("Generate msgSignatureKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public static KeyPair generateMsgEncryptionKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(MSG_ENCR_KEY_ALGO);
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.trace("Generate msgEncryptionKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String signMessage(PrivateKey privateKey, String message)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        Signature sig = Signature.getInstance(MSG_SIGN_ALGO);
        sig.initSign(privateKey);
        sig.update(message.getBytes());
        return Base64.toBase64String(sig.sign());
    }

    public static boolean verifyMessage(PublicKey publicKey, String message, String signature)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance(MSG_SIGN_ALGO);
        sig.initVerify(publicKey);
        sig.update(message.getBytes());
        return sig.verify(Base64.decode(signature));
    }

    public static byte[] signStorageData(PrivateKey privateKey, byte[] data)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        Signature sig = Signature.getInstance(MSG_SIGN_ALGO);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    public static boolean verifyStorageData(PublicKey publicKey, byte[] data, byte[] signature)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance(MSG_SIGN_ALGO);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    public static byte[] getHash(Integer data) {
        return Sha256Hash.hash(ByteBuffer.allocate(4).putInt(data).array());
    }

    public static byte[] getHash(Object data) {
        return Sha256Hash.hash(Utilities.objectToByteArray(data));
    }

    public static byte[] getHash(String message) {
        return Sha256Hash.hash(Utils.formatMessageForSigning(message));
    }

    public static String getHashAsHex(String text) {
        return Utils.HEX.encode(Sha256Hash.hash(Utils.formatMessageForSigning(text)));
    }

    public static String pubKeyToString(PublicKey publicKey) {
        final X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
        return java.util.Base64.getEncoder().encodeToString(x509EncodedKeySpec.getEncoded());
    }

    // TODO just temp for arbitrator
    public static PublicKey decodeDSAPubKeyHex(String pubKeyHex) throws NoSuchAlgorithmException, InvalidKeySpecException {
        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(Utils.HEX.decode(pubKeyHex));
        KeyFactory keyFactory = KeyFactory.getInstance("DSA");
        return keyFactory.generatePublic(pubKeySpec);
    }
}


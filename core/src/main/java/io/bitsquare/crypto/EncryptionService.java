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

package io.bitsquare.crypto;

import io.bitsquare.util.Utilities;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionService<T> {
    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ALGO_SYM = "AES";
    private static final String CIPHER_SYM = "AES";
    private static final String ALGO_ASYM = "RSA";
    private static final String CIPHER_ASYM = "RSA/ECB/PKCS1Padding";
    private static final int KEY_SIZE_SYM = 128;
    private static final int KEY_SIZE_ASYM = 1024;

    @Inject
    public EncryptionService() {
    }

    public KeyPair getGeneratedDSAKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.debug("getGeneratedDSAKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public KeyPair getGeneratedRSAKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGO_ASYM);
        keyPairGenerator.initialize(KEY_SIZE_ASYM);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.debug("getGeneratedRSAKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public Bucket encryptObject(PublicKey publicKey, Object object) throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException,
            NoSuchAlgorithmException, NoSuchPaddingException {
        return encrypt(publicKey, Utilities.objectToBytArray(object));
    }

    public T decryptToObject(PrivateKey privateKey, Bucket bucket) throws IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        return Utilities.<T>byteArrayToObject(decrypt(privateKey, bucket));
    }

    public Bucket encrypt(PublicKey publicKey, byte[] payload) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {
        long ts = System.currentTimeMillis();
        // Create symmetric key and 
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGO_SYM);
        keyGenerator.init(KEY_SIZE_SYM);
        SecretKey secretKey = keyGenerator.generateKey();

        // Encrypt secretKey with asymmetric key
        Cipher cipherAsym = Cipher.getInstance(CIPHER_ASYM);
        cipherAsym.init(Cipher.ENCRYPT_MODE, publicKey);
        log.debug("encrypt secret key length: " + secretKey.getEncoded().length);
        byte[] encryptedKey = cipherAsym.doFinal(secretKey.getEncoded());

        // Encrypt payload with symmetric key
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getEncoded(), ALGO_SYM);
        Cipher cipherSym = Cipher.getInstance(CIPHER_SYM);
        cipherSym.init(Cipher.ENCRYPT_MODE, keySpec);
        log.debug("encrypt payload length: " + payload.length);
        byte[] encryptedPayload = cipherSym.doFinal(payload);
        log.debug("Encryption needed {} ms", System.currentTimeMillis() - ts);
        return new Bucket(encryptedKey, encryptedPayload);
    }

    public byte[] decrypt(PrivateKey privateKey, Bucket bucket) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        long ts = System.currentTimeMillis();
        byte[] encryptedPayload = bucket.encryptedPayload;
        byte[] encryptedKey = bucket.encryptedKey;

        // Decrypt secretKey key with asymmetric key
        Cipher cipherAsym = Cipher.getInstance(CIPHER_ASYM);
        cipherAsym.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] secretKey = cipherAsym.doFinal(encryptedKey);
        log.debug("decrypt secret key length: " + secretKey.length);
        // Decrypt payload with symmetric key
        Key key = new SecretKeySpec(secretKey, ALGO_SYM);
        Cipher cipherSym = Cipher.getInstance(CIPHER_SYM);
        cipherSym.init(Cipher.DECRYPT_MODE, key);
        byte[] payload = cipherSym.doFinal(encryptedPayload);
        log.debug("decrypt payload length: " + payload.length);
        log.debug("Decryption needed {} ms", System.currentTimeMillis() - ts);
        return payload;
    }
}


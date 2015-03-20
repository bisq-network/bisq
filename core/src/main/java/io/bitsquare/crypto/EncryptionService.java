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

import java.io.Serializable;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionService<T> {
    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    @Inject
    public EncryptionService() {
    }

    public KeyPair getKeyPair() {
        KeyPair keyPair = null;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(1024);
            keyPair = keyPairGenerator.genKeyPair();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception at init " + e.getMessage());
        }
        return keyPair;
    }

    public Tuple encryptObject(PublicKey publicKey, Object object) {
        return encrypt(publicKey, Utilities.objectToBytArray(object));
    }

    public T decryptToObject(PrivateKey privateKey, Tuple tuple) {
        return (T) Utilities.byteArrayToObject(decrypt(privateKey, tuple));
    }

    public Tuple encrypt(PublicKey publicKey, byte[] payload) {
        byte[] encryptedPayload = null;
        byte[] encryptedKey = null;
        try {
            // Create symmetric key and 
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            SecretKey secretKey = keyGenerator.generateKey();

            // Encrypt secretKey with asymmetric key
            Cipher cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            encryptedKey = cipher.doFinal(secretKey.getEncoded());

            // Encrypt payload with symmetric key
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getEncoded(), "AES");
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            encryptedPayload = cipher.doFinal(payload);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception at encrypt " + e.getMessage());
        }
        return new Tuple(encryptedKey, encryptedPayload);
    }

    public byte[] decrypt(PrivateKey privateKey, Tuple tuple) {
        byte[] encryptedPayload = tuple.encryptedPayload;
        byte[] encryptedKey = tuple.encryptedKey;

        byte[] payload = null;
        try {
            // Decrypt secretKey key with asymmetric key
            Cipher cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] secretKey = cipher.doFinal(encryptedKey);

            // Decrypt payload with symmetric key
            Key key = new SecretKeySpec(secretKey, "AES");
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            payload = cipher.doFinal(encryptedPayload);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception at decrypt " + e.getMessage());
        }
        return payload;
    }

    public class Tuple implements Serializable {
        private static final long serialVersionUID = -8709538217388076762L;

        public final byte[] encryptedKey;
        public final byte[] encryptedPayload;

        public Tuple(byte[] encryptedKey, byte[] encryptedPayload) {
            this.encryptedKey = encryptedKey;
            this.encryptedPayload = encryptedPayload;
        }
    }
}


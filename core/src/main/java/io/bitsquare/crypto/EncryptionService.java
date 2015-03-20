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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;

public class EncryptionService {
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

    public byte[] encryptObject(PublicKey publicKey, Object object) {
        return encrypt(publicKey, Utilities.objectToBytArray(object));
    }

    public Object decryptObject(PrivateKey privateKey, byte[] cipherMessage) {
        return Utilities.byteArrayToObject(decrypt(privateKey, cipherMessage));
    }

    public byte[] encrypt(PublicKey publicKey, byte[] data) {
        byte[] cipherData = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            cipherData = cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception at encrypt " + e.getMessage());
        }
        return cipherData;
    }

    public byte[] decrypt(PrivateKey privateKey, byte[] cipherText) {
        byte[] data = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            data = cipher.doFinal(cipherText);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception at decrypt " + e.getMessage());
        }
        return data;
    }

}

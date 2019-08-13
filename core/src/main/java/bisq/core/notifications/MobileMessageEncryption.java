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

package bisq.core.notifications;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Charsets;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.security.NoSuchAlgorithmException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class MobileMessageEncryption {
    private SecretKeySpec keySpec;
    private Cipher cipher;

    @Inject
    public MobileMessageEncryption() {
    }

    public void setKey(String key) {
        keySpec = new SecretKeySpec(key.getBytes(Charsets.UTF_8), "AES");
        try {
            cipher = Cipher.getInstance("AES/CBC/NOPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    public String encrypt(String valueToEncrypt, String iv) throws Exception {
        while (valueToEncrypt.length() % 16 != 0) {
            valueToEncrypt = valueToEncrypt + " ";
        }

        if (iv.length() != 16) {
            throw new Exception("iv not 16 characters");
        }
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(Charsets.UTF_8));
        byte[] encryptedBytes = doEncrypt(valueToEncrypt, ivSpec);
        return Base64.encodeBase64String(encryptedBytes);
    }

    private byte[] doEncrypt(String text, IvParameterSpec ivSpec) throws Exception {
        if (text == null || text.length() == 0) {
            throw new Exception("Empty string");
        }

        byte[] encrypted;
        try {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            encrypted = cipher.doFinal(text.getBytes(Charsets.UTF_8));
        } catch (Exception e) {
            throw new Exception("[encrypt] " + e.getMessage());
        }
        return encrypted;
    }
}

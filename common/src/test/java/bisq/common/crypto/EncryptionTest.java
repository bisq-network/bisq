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

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EncryptionTest {
    private static final byte[] PAYLOAD = "payload of a mailbox message".getBytes(StandardCharsets.UTF_8);

    @Test
    public void decryptPayloadWithHmacReturnsPayloadWithMatchingHmac() throws CryptoException {
        SecretKey secretKey = Encryption.generateSecretKey(256);

        byte[] encryptedPayloadWithHmac = Encryption.encryptPayloadWithHmac(PAYLOAD, secretKey);

        assertArrayEquals(PAYLOAD, Encryption.decryptPayloadWithHmac(encryptedPayloadWithHmac, secretKey));
    }

    @Test
    public void decryptPayloadWithHmacRejectsHmacWithFirstOrLastByteChanged() throws CryptoException {
        SecretKey secretKey = Encryption.generateSecretKey(256);
        byte[] payloadWithHmac = Encryption.decrypt(Encryption.encryptPayloadWithHmac(PAYLOAD, secretKey), secretKey);
        int firstHmacByte = PAYLOAD.length;
        int lastHmacByte = payloadWithHmac.length - 1;

        for (int index : new int[]{firstHmacByte, lastHmacByte}) {
            byte[] payloadWithChangedHmac = payloadWithHmac.clone();
            payloadWithChangedHmac[index] ^= 1;
            byte[] encryptedPayloadWithChangedHmac = Encryption.encrypt(payloadWithChangedHmac, secretKey);

            CryptoException exception = assertThrows(CryptoException.class,
                    () -> Encryption.decryptPayloadWithHmac(encryptedPayloadWithChangedHmac, secretKey));
            assertEquals("Hmac does not match.", exception.getMessage());
        }
    }
}

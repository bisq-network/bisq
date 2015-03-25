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

import io.bitsquare.p2p.MailboxMessage;

import java.security.KeyPair;

import java.util.Random;

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.springframework.test.util.AssertionErrors.assertEquals;

public class EncryptionServiceTests {
    private static final Logger log = LoggerFactory.getLogger(EncryptionServiceTests.class);

    @Test
    public void testEncryptionWithMailboxMessage() throws Exception {
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>();
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();

        TestMessage message = new TestMessage("test");
        Bucket bucket = encryptionService.encryptObject(p2pEncryptKeyPair.getPublic(), message);
        MailboxMessage result = encryptionService.decryptToObject(p2pEncryptKeyPair.getPrivate(), bucket);
        assertEquals("", message.data, ((TestMessage) result).data);
    }

    @Test
    public void testEncryptionWithInteger() throws Exception {
        EncryptionService<Integer> encryptionService = new EncryptionService<>();
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        int data = 1234;
        Bucket bucket = encryptionService.encryptObject(p2pEncryptKeyPair.getPublic(), data);
        Integer result = encryptionService.decryptToObject(p2pEncryptKeyPair.getPrivate(), bucket);
        assertEquals("", data, result);
    }

    @Test
    public void testEncryptionWithBytes() throws Exception {
        EncryptionService encryptionService = new EncryptionService();
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();

        byte[] data = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
        Bucket bucket = encryptionService.encrypt(p2pEncryptKeyPair.getPublic(), data);
        byte[] result = encryptionService.decrypt(p2pEncryptKeyPair.getPrivate(), bucket);
        assertEquals("", result, data);
    }
    
    @Test
    public void testEncryptionWithLargeData() throws Exception {
        EncryptionService encryptionService = new EncryptionService();
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();

        byte[] data = new byte[2000];
        new Random().nextBytes(data);
        
        Bucket bucket = encryptionService.encrypt(p2pEncryptKeyPair.getPublic(), data);
        byte[] result = encryptionService.decrypt(p2pEncryptKeyPair.getPrivate(), bucket);
        assertEquals("", result, data);
    }
}

class TestMessage implements MailboxMessage {
    public String data = "test";

    public TestMessage(String data) {
        this.data = data;
    }
}

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

import org.bitcoinj.core.ECKey;

import java.security.KeyPair;

import java.util.Arrays;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class EncryptionServiceTests {
    private static final Logger log = LoggerFactory.getLogger(EncryptionServiceTests.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testEncryptionWithMailboxMessage() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();

        TestMessage data = new TestMessage("test");
        byte[] encrypted = encryptionService.encryptMessage(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        MailboxMessage decrypted = encryptionService.decryptToMessage(p2pEncryptKeyPair.getPrivate(), encrypted);
        assertEquals("", data.data, ((TestMessage) decrypted).data);
    }

    @Test
    public void testEncryptionWithInteger() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<Integer> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();
        Integer data = 1234;
        byte[] encrypted = encryptionService.encryptObject(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        Integer decrypted = encryptionService.decryptToMessage(p2pEncryptKeyPair.getPrivate(), encrypted);
        assertEquals("", data, decrypted);
    }

    @Test
    public void testEncryptionWithBytes() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();

        byte[] data = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
        byte[] encrypted = encryptionService.encryptBytes(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        byte[] decrypted = encryptionService.decryptBytes(p2pEncryptKeyPair.getPrivate(), encrypted);
        assertTrue(Arrays.equals(data, decrypted));
    }

    @Test
    public void testEncryptionWithLargeData() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();

        byte[] data = new byte[2000];
        new Random().nextBytes(data);

        byte[] encrypted = encryptionService.encryptBytes(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        byte[] decrypted = encryptionService.decryptBytes(p2pEncryptKeyPair.getPrivate(), encrypted);
        assertTrue(Arrays.equals(data, decrypted));
    }

    @Test
    public void testEncryptionWithTooMuchData() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();

        byte[] data = new byte[11000];
        new Random().nextBytes(data);

        thrown.expect(CryptoException.class);
        thrown.expectMessage("The data exceeds the max. size.");

        byte[] encrypted = encryptionService.encryptBytes(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        byte[] decrypted = encryptionService.decryptBytes(p2pEncryptKeyPair.getPrivate(), encrypted);
    }

    @Test
    public void testEncryptionWithTooLessData() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();

        byte[] data = new byte[11000];
        new Random().nextBytes(data);

        thrown.expect(CryptoException.class);
        thrown.expectMessage("The data is shorter as the min. overhead length.");

        byte[] encrypted = encryptionService.encryptBytes(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        byte[] decrypted = encryptionService.decryptBytes(p2pEncryptKeyPair.getPrivate(), new byte[24]);
    }

    @Test
    public void testEncryptionWithManipulatedPayload() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();

        byte[] data = new byte[110];
        new Random().nextBytes(data);

        thrown.expect(CryptoException.class);
        thrown.expectMessage("The checksum is invalid.");

        byte[] encrypted = encryptionService.encryptBytes(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        byte[] manipulated = new byte[encrypted.length];
        System.arraycopy(encrypted, 0, manipulated, 0, manipulated.length);
        manipulated[manipulated.length - 1] = 0x31;
        byte[] decrypted = encryptionService.decryptBytes(p2pEncryptKeyPair.getPrivate(), manipulated);
    }

    @Test
    public void testEncryptionWithWrongVersion() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();

        byte[] data = new byte[1100];
        new Random().nextBytes(data);

        thrown.expect(CryptoException.class);
        thrown.expectMessage("Incorrect version.");

        byte[] encrypted = encryptionService.encryptBytes(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        byte[] manipulated = new byte[encrypted.length];
        System.arraycopy(encrypted, 0, manipulated, 0, manipulated.length);
        manipulated[0] = 0x02;
        byte[] decrypted = encryptionService.decryptBytes(p2pEncryptKeyPair.getPrivate(), manipulated);
    }

    @Test
    public void testEncryptionWithNoData() throws Exception {
        SignatureService signatureService = new SignatureService();
        EncryptionService<MailboxMessage> encryptionService = new EncryptionService<>(signatureService);
        KeyPair p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
        ECKey signatureKeyPair = new ECKey();

        byte[] data = new byte[0];

        thrown.expect(CryptoException.class);
        thrown.expectMessage("Input data is null.");

        byte[] encrypted = encryptionService.encryptBytes(p2pEncryptKeyPair.getPublic(), signatureKeyPair, data);
        byte[] decrypted = encryptionService.decryptBytes(p2pEncryptKeyPair.getPrivate(), encrypted);
    }
}

class TestMessage implements MailboxMessage {
    public String data = "test";

    public TestMessage(String data) {
        this.data = data;
    }
}

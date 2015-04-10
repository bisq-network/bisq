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
import io.bitsquare.util.Utilities;

import java.io.File;
import java.io.IOException;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class CryptoServiceTests {
    private static final Logger log = LoggerFactory.getLogger(CryptoServiceTests.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private PubKeyRing pubKeyRing;
    private KeyRing keyRing;
    private File dir = new File("/tmp/bitsquare_tests");

    @Before
    public void setup() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        dir.mkdir();
        KeyStorage keyStorage = new KeyStorage(dir);
        keyRing = new KeyRing(keyStorage);
        pubKeyRing = keyRing.getPubKeyRing();
    }

    @After
    public void tearDown() throws IOException {
        Utilities.deleteDirectory(dir);
    }

    @Test
    public void testDecryptAndVerifyMessage() throws CryptoException {
        CryptoService<MailboxMessage> encryptionService = new CryptoService<>(keyRing);
        TestMessage data = new TestMessage("test");
        SealedAndSignedMessage encrypted = encryptionService.encryptAndSignMessage(pubKeyRing, data);
        MessageWithPubKey decrypted = encryptionService.decryptAndVerifyMessage(encrypted);
        assertEquals("", data.data, ((TestMessage) decrypted.getMessage()).data);
    }

}

class TestMessage implements MailboxMessage {
    public String data = "test";

    public TestMessage(String data) {
        this.data = data;
    }
}

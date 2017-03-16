/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.crypto;


import io.bisq.app.Version;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.wire.proto.Messages;
import io.bisq.messages.DecryptedMsgWithPubKey;
import io.bisq.messages.Message;
import io.bisq.messages.NodeAddress;
import io.bisq.messages.crypto.*;
import io.bisq.messages.p2p.messaging.MailboxMessage;
import io.bisq.messages.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bisq.p2p.peers.keepalive.messages.Ping;
import io.bisq.storage.FileUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.Random;
import java.util.UUID;

import static io.bisq.crypto.EncryptionService.decryptHybridWithSignature;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EncryptionServiceTests {
    private static final Logger log = LoggerFactory.getLogger(EncryptionServiceTests.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private PubKeyRing pubKeyRing;
    private KeyRing keyRing;
    private File dir;

    @Before
    public void setup() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        Security.addProvider(new BouncyCastleProvider());
        dir = File.createTempFile("temp_tests", "");
        dir.delete();
        dir.mkdir();
        KeyStorage keyStorage = new KeyStorage(dir);
        keyRing = new KeyRing(keyStorage);
        pubKeyRing = keyRing.getPubKeyRing();
    }

    @After
    public void tearDown() throws IOException {
        FileUtil.deleteDirectory(dir);
    }

    @Test
    public void testDecryptAndVerifyMessage() throws CryptoException {
        EncryptionService encryptionService = new EncryptionService(keyRing);
        TestMessage data = new TestMessage("test");
        PrefixedSealedAndSignedMessage encrypted = new PrefixedSealedAndSignedMessage(null,
                encryptionService.encryptAndSign(pubKeyRing, data),
                Hash.getHash("aa"));
        DecryptedMsgWithPubKey decrypted = encryptionService.decryptAndVerify(encrypted.sealedAndSigned);
        assertEquals(data.data, ((TestMessage) decrypted.message).data);
    }


    @Test
    public void testDecryptHybridWithSignature() {
        long ts = System.currentTimeMillis();
        log.trace("start ");
        for (int i = 0; i < 100; i++) {
            Ping payload = new Ping(new Random().nextInt(), 10);
            SealedAndSigned sealedAndSigned = null;
            try {
                sealedAndSigned = Encryption.encryptHybridWithSignature(payload,
                        keyRing.getSignatureKeyPair(), keyRing.getPubKeyRing().getEncryptionPubKey());
            } catch (CryptoException e) {
                log.error("encryptHybridWithSignature failed");
                e.printStackTrace();
                assertTrue(false);
            }
            try {
                DecryptedDataTuple tuple = decryptHybridWithSignature(sealedAndSigned, keyRing.getEncryptionKeyPair().getPrivate());
                assertEquals(((Ping) tuple.payload).nonce, payload.nonce);
            } catch (CryptoException e) {
                log.error("decryptHybridWithSignature failed");
                e.printStackTrace();
                assertTrue(false);
            }
        }
        log.trace("took " + (System.currentTimeMillis() - ts) + " ms.");
    }

    private static class MockMessage implements Message {
        public int nonce;

        public MockMessage(int nonce) {
            this.nonce = nonce;
        }

        @Override
        public int getMessageVersion() {
            return 0;
        }

        @Override
        public Messages.Envelope toProtoBuf() {
            return Messages.Envelope.newBuilder().setPing(Messages.Ping.newBuilder().setNonce(nonce)).build();
        }
    }
}

final class TestMessage implements MailboxMessage {
    public String data = "test";
    private final int messageVersion = Version.getP2PMessageVersion();
    private String uid;

    public TestMessage(String data) {
        this.data = data;
        uid = UUID.randomUUID().toString();
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return null;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public Messages.Envelope toProtoBuf() {
        throw new NotImplementedException();
    }
}

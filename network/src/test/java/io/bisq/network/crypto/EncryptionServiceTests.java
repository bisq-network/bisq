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

package io.bisq.network.crypto;


import io.bisq.common.app.Version;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.Hash;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.common.storage.FileUtil;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.protobuffer.crypto.DecryptedDataTuple;
import io.bisq.protobuffer.crypto.ProtoCryptoUtil;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.alert.PrivateNotificationMessage;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.message.p2p.PrefixedSealedAndSignedMessage;
import io.bisq.protobuffer.message.p2p.peers.keepalive.Ping;
import io.bisq.protobuffer.payload.alert.PrivateNotificationPayload;
import io.bisq.protobuffer.payload.crypto.SealedAndSignedPayload;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.vo.crypto.KeyRingVO;
import io.bisq.vo.crypto.PubKeyRingVO;
import io.bisq.vo.crypto.SealedAndSignedVO;
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

import static io.bisq.network.crypto.EncryptionService.decryptHybridWithSignature;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EncryptionServiceTests {
    private static final Logger log = LoggerFactory.getLogger(EncryptionServiceTests.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private PubKeyRingVO pubKeyRingVO;
    private KeyRingVO keyRingVO;
    private File dir;

    @Before
    public void setup() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        Security.addProvider(new BouncyCastleProvider());
        dir = File.createTempFile("temp_tests", "");
        dir.delete();
        dir.mkdir();
        KeyStorage keyStorage = new KeyStorage(dir);
        keyRingVO = new KeyRingVO(keyStorage);
        pubKeyRingVO = keyRingVO.getPubKeyRingVO();
    }

    @After
    public void tearDown() throws IOException {
        FileUtil.deleteDirectory(dir);
    }

    @Test
    public void testDecryptAndVerifyMessage() throws CryptoException {
        EncryptionService encryptionService = new EncryptionService(keyRingVO);
        final PrivateNotificationPayload privateNotification = new PrivateNotificationPayload("test");
        privateNotification.setSigAndPubKey("", pubKeyRingVO.getSignaturePubKey());
        final NodeAddress nodeAddress = new NodeAddress("localhost", 2222);
        PrivateNotificationMessage data = new PrivateNotificationMessage(privateNotification,
                nodeAddress,
                UUID.randomUUID().toString());
        PrefixedSealedAndSignedMessage encrypted = new PrefixedSealedAndSignedMessage(nodeAddress,
                new SealedAndSignedPayload(encryptionService.encryptAndSign(pubKeyRingVO, data)),
                Hash.getHash("localhost"),
                UUID.randomUUID().toString());
        DecryptedMsgWithPubKey decrypted = encryptionService.decryptAndVerify(encrypted.sealedAndSignedPayload.get());
        assertEquals(data.privateNotificationPayload.message,
                ((PrivateNotificationMessage) decrypted.message).privateNotificationPayload.message);
    }


    @Test
    public void testDecryptHybridWithSignature() {
        long ts = System.currentTimeMillis();
        log.trace("start ");
        for (int i = 0; i < 100; i++) {
            Ping payload = new Ping(new Random().nextInt(), 10);
            SealedAndSignedVO sealedAndSignedVO = null;
            try {
                sealedAndSignedVO = ProtoCryptoUtil.encryptHybridWithSignature(payload,
                        keyRingVO.getSignatureKeyPair(), keyRingVO.getPubKeyRingVO().getEncryptionPubKey());
            } catch (CryptoException e) {
                log.error("encryptHybridWithSignature failed");
                e.printStackTrace();
                assertTrue(false);
            }
            try {
                DecryptedDataTuple tuple = decryptHybridWithSignature(sealedAndSignedVO, keyRingVO.getEncryptionKeyPair().getPrivate());
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
        public final int nonce;

        public MockMessage(int nonce) {
            this.nonce = nonce;
        }

        @Override
        public int getMessageVersion() {
            return 0;
        }

        @Override
        public PB.Envelope toProto() {
            return PB.Envelope.newBuilder().setPing(PB.Ping.newBuilder().setNonce(nonce)).build();
        }
    }
}

final class TestMessage implements MailboxMessage {
    public String data = "test";
    private final int messageVersion = Version.getP2PMessageVersion();
    private final String uid;

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
    public PB.Envelope toProto() {
        throw new NotImplementedException();
    }
}

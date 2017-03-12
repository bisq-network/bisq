package io.bisq.p2p.storage.messages;

import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.wire.proto.Messages;
import io.bisq.p2p.NodeAddress;
import io.bisq.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bisq.p2p.network.ProtoBufferUtilities;
import io.bisq.p2p.storage.payload.MailboxStoragePayload;
import io.bisq.p2p.storage.storageentry.ProtectedMailboxStorageEntry;
import io.bisq.p2p.storage.storageentry.ProtectedStorageEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

import static org.junit.Assert.assertEquals;

@Slf4j
public class AddDataMessageTest {
    private KeyRing keyRing1;
    private File dir1;


    @Before
    public void setup() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Security.addProvider(new BouncyCastleProvider());
        dir1 = File.createTempFile("temp_tests1", "");
        dir1.delete();
        dir1.mkdir();
        keyRing1 = new KeyRing(new KeyStorage(dir1));
    }

    @Test
    public void toProtoBuf() throws Exception {
        SealedAndSigned sealedAndSigned = new SealedAndSigned(RandomUtils.nextBytes(10), RandomUtils.nextBytes(10), RandomUtils.nextBytes(10), keyRing1.getPubKeyRing().getSignaturePubKey());
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(new NodeAddress("host", 1000), sealedAndSigned, RandomUtils.nextBytes(10));
        MailboxStoragePayload mailboxStoragePayload = new MailboxStoragePayload(prefixedSealedAndSignedMessage, keyRing1.getPubKeyRing().getSignaturePubKey(), keyRing1.getPubKeyRing().getSignaturePubKey());
        ProtectedStorageEntry protectedStorageEntry = new ProtectedMailboxStorageEntry(mailboxStoragePayload, keyRing1.getSignatureKeyPair().getPublic(), 1, RandomUtils.nextBytes(10), keyRing1.getPubKeyRing().getSignaturePubKey());
        AddDataMessage dataMessage1 = new AddDataMessage(protectedStorageEntry);
        Messages.Envelope envelope = dataMessage1.toProtoBuf();
        AddDataMessage dataMessage2 = (AddDataMessage) ProtoBufferUtilities.getAddDataMessage(envelope);
        assertEquals(dataMessage1, dataMessage2);
    }

}
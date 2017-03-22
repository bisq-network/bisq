package io.bisq.network.p2p.storage.messages;

import io.bisq.common.crypto.CryptoException;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.ProtoBufferUtilities;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.crypto.KeyStorage;
import io.bisq.protobuffer.message.p2p.PrefixedSealedAndSignedMessage;
import io.bisq.protobuffer.message.p2p.storage.AddDataMessage;
import io.bisq.protobuffer.payload.crypto.SealedAndSigned;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.p2p.storage.MailboxStoragePayload;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedMailboxStorageEntry;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedStorageEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

import static org.junit.Assert.assertTrue;

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
        MailboxStoragePayload mailboxStoragePayload = new MailboxStoragePayload(prefixedSealedAndSignedMessage,
                keyRing1.getPubKeyRing().getSignaturePubKey(), keyRing1.getPubKeyRing().getSignaturePubKey());
        ProtectedStorageEntry protectedStorageEntry = new ProtectedMailboxStorageEntry(mailboxStoragePayload,
                keyRing1.getSignatureKeyPair().getPublic(), 1, RandomUtils.nextBytes(10), keyRing1.getPubKeyRing().getSignaturePubKey());
        AddDataMessage dataMessage1 = new AddDataMessage(protectedStorageEntry);
        PB.Envelope envelope = dataMessage1.toProto();
        AddDataMessage dataMessage2 = (AddDataMessage) ProtoBufferUtilities.getAddDataMessage(envelope);

        assertTrue(dataMessage1.protectedStorageEntry.getStoragePayload().equals(dataMessage2.protectedStorageEntry.getStoragePayload()));
        assertTrue(dataMessage1.protectedStorageEntry.equals(dataMessage2.protectedStorageEntry));
        assertTrue(dataMessage1.equals(dataMessage2));
    }

}
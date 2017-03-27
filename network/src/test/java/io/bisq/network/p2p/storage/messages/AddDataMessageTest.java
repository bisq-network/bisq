package io.bisq.network.p2p.storage.messages;

import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.ProtoBufferUtilities;
import io.bisq.protobuffer.message.p2p.PrefixedSealedAndSignedMessage;
import io.bisq.protobuffer.message.p2p.storage.AddDataMessage;
import io.bisq.protobuffer.payload.crypto.SealedAndSignedPayload;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.p2p.storage.MailboxStoragePayload;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedMailboxStorageEntry;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedStorageEntry;
import io.bisq.vo.crypto.KeyRingVO;
import io.bisq.vo.crypto.SealedAndSignedVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

@Slf4j
public class AddDataMessageTest {
    private KeyRingVO keyRingVO1;
    private File dir1;


    @Before
    public void setup() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Security.addProvider(new BouncyCastleProvider());
        dir1 = File.createTempFile("temp_tests1", "");
        dir1.delete();
        dir1.mkdir();
        keyRingVO1 = new KeyRingVO(new KeyStorage(dir1));
    }

    @Test
    public void toProtoBuf() throws Exception {
        SealedAndSignedVO sealedAndSignedVO = new SealedAndSignedVO(RandomUtils.nextBytes(10), RandomUtils.nextBytes(10), RandomUtils.nextBytes(10), keyRingVO1.getPubKeyRingVO().getSignaturePubKey());
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(
                new NodeAddress("host", 1000),
                new SealedAndSignedPayload(sealedAndSignedVO),
                RandomUtils.nextBytes(10),
                UUID.randomUUID().toString());
        MailboxStoragePayload mailboxStoragePayload = new MailboxStoragePayload(prefixedSealedAndSignedMessage,
                keyRingVO1.getPubKeyRingVO().getSignaturePubKey(), keyRingVO1.getPubKeyRingVO().getSignaturePubKey());
        ProtectedStorageEntry protectedStorageEntry = new ProtectedMailboxStorageEntry(mailboxStoragePayload,
                keyRingVO1.getSignatureKeyPair().getPublic(), 1, RandomUtils.nextBytes(10), keyRingVO1.getPubKeyRingVO().getSignaturePubKey());
        AddDataMessage dataMessage1 = new AddDataMessage(protectedStorageEntry);
        PB.Envelope envelope = dataMessage1.toProto();
        AddDataMessage dataMessage2 = (AddDataMessage) ProtoBufferUtilities.getAddDataMessage(envelope);

        assertTrue(dataMessage1.protectedStorageEntry.getStoragePayload().equals(dataMessage2.protectedStorageEntry.getStoragePayload()));
        assertTrue(dataMessage1.protectedStorageEntry.equals(dataMessage2.protectedStorageEntry));
        assertTrue(dataMessage1.equals(dataMessage2));
    }

}
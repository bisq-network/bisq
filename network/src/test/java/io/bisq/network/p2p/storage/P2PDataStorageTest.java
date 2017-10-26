package io.bisq.network.p2p.storage;

import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.FileUtil;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.TestUtils;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.peers.Broadcaster;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.extern.slf4j.Slf4j;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@RunWith(JMockit.class)
@Ignore("Use NetworkProtoResolver, PersistenceProtoResolver or ProtoResolver which are all in io.bisq.common.")
public class P2PDataStorageTest {
    private final Set<NodeAddress> seedNodes = new HashSet<>();
    private EncryptionService encryptionService1, encryptionService2;
    private P2PDataStorage dataStorage1;
    private KeyPair storageSignatureKeyPair1, storageSignatureKeyPair2;
    private KeyRing keyRing1, keyRing2;
    private ProtectedStoragePayload protectedStoragePayload;
    private File dir1;
    private File dir2;

    @Mocked
    Broadcaster broadcaster;
    @Mocked
    NetworkNode networkNode;
    @Mocked
    NetworkProtoResolver networkProtoResolver;
    @Mocked
    PersistenceProtoResolver persistenceProtoResolver;

    @Before
    public void setup() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Security.addProvider(new BouncyCastleProvider());
        dir1 = File.createTempFile("temp_tests1", "");
        //noinspection ResultOfMethodCallIgnored
        dir1.delete();
        //noinspection ResultOfMethodCallIgnored
        dir1.mkdir();
        dir2 = File.createTempFile("temp_tests2", "");
        //noinspection ResultOfMethodCallIgnored
        dir2.delete();
        //noinspection ResultOfMethodCallIgnored
        dir2.mkdir();

        keyRing1 = new KeyRing(new KeyStorage(dir1));
        storageSignatureKeyPair1 = keyRing1.getSignatureKeyPair();
        encryptionService1 = new EncryptionService(keyRing1, TestUtils.getNetworkProtoResolver());

        // for mailbox
        keyRing2 = new KeyRing(new KeyStorage(dir2));
        storageSignatureKeyPair2 = keyRing2.getSignatureKeyPair();
        encryptionService2 = new EncryptionService(keyRing2, TestUtils.getNetworkProtoResolver());
        //dataStorage1 = new P2PDataStorage(broadcaster, networkNode, dir1, persistenceProtoResolver);
    }

    @After
    public void tearDown() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Path path = Paths.get(TestUtils.test_dummy_dir);
        File dir = path.toFile();
        FileUtil.deleteDirectory(dir);
        FileUtil.deleteDirectory(dir1);
        FileUtil.deleteDirectory(dir2);
    }

   /* @Test
    public void testProtectedStorageEntryAddAndRemove() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        storagePayload = new AlertPayload(new AlertVO("alert",
                false,
                "version",
                storageSignatureKeyPair1.getPublic().getEncoded(),
                "sig",
                null));

        ProtectedStorageEntry data = dataStorage1.getProtectedData(storagePayload, storageSignatureKeyPair1);
        assertTrue(dataStorage1.add(data, null, null, true));
        assertEquals(1, dataStorage1.getMap().size());

        int newSequenceNumber = data.sequenceNumber + 1;
        byte[] hashOfDataAndSeqNr = Hash.getHash(new P2PDataStorage.DataAndSeqNrPair(data.getStoragePayload(), newSequenceNumber));
        byte[] signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        ProtectedStorageEntry dataToRemove = new ProtectedStorageEntry(data.getStoragePayload(), data.ownerPubKey, newSequenceNumber, signature);
        assertTrue(dataStorage1.remove(dataToRemove, null, true));
        assertEquals(0, dataStorage1.getMap().size());
    }

    @Test
    public void testProtectedStorageEntryRoundtrip() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        //mockData = new MockData("mockData", keyRing1.getSignatureKeyPair().getPublic());
        storagePayload = getDummyOffer();

        ProtectedStorageEntry data = dataStorage1.getProtectedData(storagePayload, storageSignatureKeyPair1);
        setSignature(data);
        assertTrue(checkSignature(data));

        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        data.toEnvelopeProto().writeTo(byteOutputStream);

        //TODO Use NetworkProtoResolver, PersistenceProtoResolver or ProtoResolver which are all in io.bisq.common.
       ProtectedStorageEntry protectedStorageEntry = ProtoBufferUtilities.getProtectedStorageEntry(PB.ProtectedStorageEntry.parseFrom(new ByteArrayInputStream(byteOutputStream.toByteArray())));

        assertTrue(Arrays.equals(Hash.getHash(data.getStoragePayload()), Hash.getHash(protectedStorageEntry.getStoragePayload())));
        assertTrue(data.equals(protectedStorageEntry));
        assertTrue(checkSignature(protectedStorageEntry));
    }*/

    //TODO Use NetworkProtoResolver, PersistenceProtoResolver or ProtoResolver which are all in io.bisq.common.
   /* @Test
    public void testOfferRoundtrip() throws InvalidProtocolBufferException {
        OfferPayload offer = getDummyOffer();
        try {
            String buffer = JsonFormat.printer().print(offer.toEnvelopeProto().getOfferPayload());
            JsonFormat.Parser parser = JsonFormat.parser();
            PB.OfferPayload.Builder builder = PB.OfferPayload.newBuilder();
            parser.merge(buffer, builder);
            assertEquals(offer, ProtoBufferUtilities.getOfferPayload(builder.build()));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }*/

   /* @NotNull
    private OfferPayload getDummyOffer() {
        NodeAddress nodeAddress = new NodeAddress("host", 1000);
        NodeAddress nodeAddress2 = new NodeAddress("host1", 1001);
        NodeAddress nodeAddress3 = new NodeAddress("host2", 1002);
        NodeAddress nodeAddress4 = new NodeAddress("host3", 1002);
        return new OfferPayload("id",
                System.currentTimeMillis(),
                nodeAddress4,
                keyRing1.getPubKeyRing(),
                OfferPayload.Direction.BUY,
                1200,
                1.5,
                true,
                100,
                50,
                "BTC",
                "USD",
                Lists.newArrayList(nodeAddress,
                        nodeAddress2,
                        nodeAddress3),
                Lists.newArrayList(nodeAddress,
                        nodeAddress2,
                        nodeAddress3),
                "SEPA",
                "accountid",
                "feetxId",
                "BE",
                Lists.newArrayList("BE", "AU"),
                "bankid",
                Lists.newArrayList("BANK1", "BANK2"),
                "version",
                100,
                100,
                100,
                100,
                1000,
                1000,
                1000,
                false,
                false,

                1000,
                1000,
                false,
                "hash",
                null);
    }

    private void setSignature(ProtectedStorageEntry entry) throws CryptoException {
        int newSequenceNumber = entry.sequenceNumber;
        byte[] hashOfDataAndSeqNr = Hash.getHash(new P2PDataStorage.DataAndSeqNrPair(entry.getStoragePayload(), newSequenceNumber));
        byte[] signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        entry.signature = signature;
    }

    private boolean checkSignature(ProtectedStorageEntry entry) throws CryptoException {
        byte[] hashOfDataAndSeqNr = Hash.getHash(new P2PDataStorage.DataAndSeqNrPair(entry.getStoragePayload(), entry.sequenceNumber));
        return dataStorage1.checkSignature(entry.ownerPubKey, hashOfDataAndSeqNr, entry.signature);
    }*/
}

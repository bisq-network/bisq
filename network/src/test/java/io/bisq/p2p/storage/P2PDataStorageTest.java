package io.bisq.p2p.storage;

import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.Sig;
import io.bisq.common.util.Utilities;
import io.bisq.common.wire.proto.Messages;
import io.bisq.crypto.EncryptionService;
import io.bisq.network_messages.NodeAddress;
import io.bisq.network_messages.alert.Alert;
import io.bisq.network_messages.crypto.Hash;
import io.bisq.network_messages.crypto.KeyRing;
import io.bisq.network_messages.crypto.KeyStorage;
import io.bisq.network_messages.trade.offer.payload.OfferPayload;
import io.bisq.p2p.TestUtils;
import io.bisq.p2p.network.NetworkNode;
import io.bisq.p2p.network.ProtoBufferUtilities;
import io.bisq.p2p.peers.Broadcaster;
import io.bisq.p2p.storage.mocks.MockData;
import io.bisq.storage.FileUtil;
import io.bisq.network_messages.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bisq.network_messages.payload.StoragePayload;
import lombok.extern.slf4j.Slf4j;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import sun.security.provider.DSAPublicKeyImpl;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@Slf4j
@RunWith(JMockit.class)
public class P2PDataStorageTest {
    private final Set<NodeAddress> seedNodes = new HashSet<>();
    private EncryptionService encryptionService1, encryptionService2;
    private P2PDataStorage dataStorage1;
    private KeyPair storageSignatureKeyPair1, storageSignatureKeyPair2;
    private KeyRing keyRing1, keyRing2;
    private MockData mockData;
    private StoragePayload storagePayload;
    private File dir1;
    private File dir2;

    @Mocked
    Broadcaster broadcaster;
    @Mocked
    NetworkNode networkNode;

    @Before
    public void setup() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Security.addProvider(new BouncyCastleProvider());
        dir1 = File.createTempFile("temp_tests1", "");
        dir1.delete();
        dir1.mkdir();
        dir2 = File.createTempFile("temp_tests2", "");
        dir2.delete();
        dir2.mkdir();

        keyRing1 = new KeyRing(new KeyStorage(dir1));

        storageSignatureKeyPair1 = keyRing1.getSignatureKeyPair();
        encryptionService1 = new EncryptionService(keyRing1);

        // for mailbox
        keyRing2 = new KeyRing(new KeyStorage(dir2));
        storageSignatureKeyPair2 = keyRing2.getSignatureKeyPair();
        encryptionService2 = new EncryptionService(keyRing2);
        dataStorage1 = new P2PDataStorage(broadcaster, networkNode, dir1);


    }

    @After
    public void tearDown() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Path path = Paths.get(TestUtils.test_dummy_dir);
        File dir = path.toFile();
        FileUtil.deleteDirectory(dir);
        FileUtil.deleteDirectory(dir1);
        FileUtil.deleteDirectory(dir2);
    }

    @Test
    public void testAddAndRemove() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        //mockData = new MockData("mockData", keyRing1.getSignatureKeyPair().getPublic());
        storagePayload = new Alert("alert", false, "version", storageSignatureKeyPair2.getPublic().getEncoded(),
                "sig");

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
    public void testAddAndRemove2() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        //mockData = new MockData("mockData", keyRing1.getSignatureKeyPair().getPublic());
        storagePayload = getDummyOffer();

        ProtectedStorageEntry data = dataStorage1.getProtectedData(storagePayload, storageSignatureKeyPair1);
        setSignature(data);
        assertTrue(checkSignature(data));

        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        ((Messages.ProtectedStorageEntry) data.toProtoBuf()).writeTo(byteOutputStream);
        ProtectedStorageEntry protectedStorageEntry = ProtoBufferUtilities.getProtectedStorageEntry(Messages.ProtectedStorageEntry.parseFrom(new ByteArrayInputStream(byteOutputStream.toByteArray())));
        data.equals(protectedStorageEntry);
        assertEquals(Hash.getHash(data.getStoragePayload()), Hash.getHash(protectedStorageEntry.getStoragePayload()));
        assertEquals(data, protectedStorageEntry);
        assertTrue(checkSignature(protectedStorageEntry));
    }

    @Test
    public void testOfferRoundtrip() throws InvalidProtocolBufferException {
        OfferPayload offer1 = getDummyOffer();
        byte[] serialize = Utilities.serialize(offer1);
        byte[] serialize2 = Utilities.serialize(offer1);
        OfferPayload offer1des = Utilities.deserialize(serialize);
        assertTrue(Arrays.equals(serialize, serialize2));
        assertTrue(Arrays.equals(Utilities.serialize(offer1des), serialize));

        try {
            String buffer = JsonFormat.printer().print(offer1.toProtoBuf().getOffer());
            JsonFormat.Parser parser = JsonFormat.parser();
            Messages.Offer.Builder builder = Messages.Offer.newBuilder();
            parser.merge(buffer, builder);
            OfferPayload offer2 = ProtoBufferUtilities.getOffer(builder.build());
            assertEquals(offer1, offer2);
            for (int i = 0; i < offer1.getArbitratorNodeAddresses().size(); i++) {
                if (!offer1.getArbitratorNodeAddresses().get(i).equals(offer2.getArbitratorNodeAddresses().get(i)))
                    fail();
            }
            byte[] offerserNode1 = Utilities.serialize((Serializable) offer1.getArbitratorNodeAddresses());
            byte[] offerserNode2 = Utilities.serialize((Serializable) offer2.getArbitratorNodeAddresses());
            writeSer(offerserNode1, "out_arbit_1.bin");
            writeSer(offerserNode2, "out_arbit_2.bin");

            byte[] offerser1 = Utilities.serialize(offer1);
            byte[] offerser2 = Utilities.serialize(offer2);
            byte[] offerser3 = Utilities.serialize(offer2);
            byte[] offerProto = offer1.toProtoBuf().toByteString().toByteArray();
            byte[] offerProto1 = offer2.toProtoBuf().toByteString().toByteArray();

            writeSer(offerser1, "out1.bin");
            writeSer(offerser2, "out2.bin");

            assertTrue(Arrays.equals(offerserNode1, offerserNode2));
            assertTrue(Arrays.equals(offerProto, offerProto1));
            assertTrue(Arrays.equals(offerser2, offerser3));
            assertTrue(Arrays.equals(offerser1, offerser2));
            assertTrue(Arrays.equals(Hash.getHash(offer1), Hash.getHash(offer2)));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    private void writeSer(byte[] ser, String file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(ser);
        fos.close();
    }

    @NotNull
    private OfferPayload getDummyOffer() {
        NodeAddress nodeAddress = new NodeAddress("host", 1000);
        NodeAddress nodeAddress2 = new NodeAddress("host1", 1001);
        NodeAddress nodeAddress3 = new NodeAddress("host2", 1002);
        NodeAddress nodeAddress4 = new NodeAddress("host3", 1002);
        return new OfferPayload("id",
                System.currentTimeMillis(),
                nodeAddress4,
                keyRing1.getPubKeyRing(),
                Offer.Direction.BUY,
                1200,
                1.5,
                true,
                100,
                50,
                "USD",
                Lists.newArrayList(nodeAddress,
                        nodeAddress2,
                        nodeAddress3),
                "SEPA",
                "accountid",
                "feetxId",
                "BE",
                Lists.newArrayList("BE", "AU"),
                "bankid",
                Lists.newArrayList("BANK1", "BANK2"), null,
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

    @Test
    public void testProtectedStorageEntryRoundtrip() throws InvalidProtocolBufferException {
        NodeAddress nodeAddress = new NodeAddress("host", 1000);
        OfferPayload offer = getDummyOffer();
        try {
            String buffer = JsonFormat.printer().print(offer.toProtoBuf().getOffer());
            JsonFormat.Parser parser = JsonFormat.parser();
            Messages.Offer.Builder builder = Messages.Offer.newBuilder();
            parser.merge(buffer, builder);
            assertEquals(offer, ProtoBufferUtilities.getOffer(builder.build()));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
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
    }


    public void testProtoAndBack() {
        P2PDataStorage p2PDataStorage = new P2PDataStorage(null, null, null);

        //p2PDataStorage.add();
        byte[] bytes = new byte[10];
        try {
            boolean result = p2PDataStorage.checkSignature(new DSAPublicKeyImpl(bytes), bytes, bytes);
            assertFalse(result);

/*
            ProtectedDataStorageTest



            byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(protectedStorageEntry.getStoragePayload(), protectedStorageEntry.sequenceNumber));
            return checkSignature(protectedStorageEntry.ownerPubKey, hashOfDataAndSeqNr, protectedStorageEntry.signature);

*/


        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

    }

}
package io.bitsquare.p2p.storage;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.*;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.crypto.SealedAndSignedMessage;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.TestUtils;
import io.bitsquare.p2p.mocks.MockMessage;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.PeerGroup;
import io.bitsquare.p2p.storage.data.DataAndSeqNr;
import io.bitsquare.p2p.storage.data.ExpirableMailboxPayload;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.p2p.storage.data.ProtectedMailboxData;
import io.bitsquare.p2p.storage.mocks.MockData;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

@Ignore
public class ProtectedDataStorageTest {
    private static final Logger log = LoggerFactory.getLogger(ProtectedDataStorageTest.class);

    boolean useClearNet = true;
    private Set<Address> seedNodes = new HashSet<>();
    private NetworkNode networkNode1;
    private PeerGroup peerGroup1;
    private EncryptionService encryptionService1, encryptionService2;
    private ProtectedExpirableDataStorage dataStorage1;
    private KeyPair storageSignatureKeyPair1, storageSignatureKeyPair2;
    private KeyRing keyRing1, keyRing2;
    private MockData mockData;
    private int sleepTime = 100;
    private File dir1;
    private File dir2;

    @Before
    public void setup() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Security.addProvider(new BouncyCastleProvider());
        dir1 = File.createTempFile("temp_tests1", "");
        dir1.delete();
        dir1.mkdir();
        dir2 = File.createTempFile("temp_tests2", "");
        dir2.delete();
        dir2.mkdir();

        UserThread.setExecutor(Executors.newSingleThreadExecutor());
        ProtectedExpirableDataStorage.CHECK_TTL_INTERVAL = 10 * 60 * 1000;

        keyRing1 = new KeyRing(new KeyStorage(dir1));

        storageSignatureKeyPair1 = keyRing1.getSignatureKeyPair();
        encryptionService1 = new EncryptionService(keyRing1);
        networkNode1 = TestUtils.getAndStartSeedNode(8001, encryptionService1, keyRing1, useClearNet, seedNodes).getP2PService().getNetworkNode();
        peerGroup1 = new PeerGroup(networkNode1, seedNodes);
        dataStorage1 = new ProtectedExpirableDataStorage(peerGroup1, new File("dummy"));

        // for mailbox
        keyRing2 = new KeyRing(new KeyStorage(dir2));
        storageSignatureKeyPair2 = keyRing2.getSignatureKeyPair();
        encryptionService2 = new EncryptionService(keyRing2);

        mockData = new MockData("mockData", keyRing1.getSignatureKeyPair().getPublic());
        Thread.sleep(sleepTime);
    }

    @After
    public void tearDown() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Thread.sleep(sleepTime);
        if (dataStorage1 != null) dataStorage1.shutDown();
        if (peerGroup1 != null) peerGroup1.shutDown();

        if (networkNode1 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            networkNode1.shutDown(() -> shutDownLatch.countDown());
            shutDownLatch.await();
        }
    }

    @Test
    public void testAddAndRemove() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        ProtectedData data = dataStorage1.getDataWithSignedSeqNr(mockData, storageSignatureKeyPair1);
        Assert.assertTrue(dataStorage1.add(data, null));
        Assert.assertEquals(1, dataStorage1.getMap().size());

        int newSequenceNumber = data.sequenceNumber + 1;
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        byte[] signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        ProtectedData dataToRemove = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertTrue(dataStorage1.remove(dataToRemove, null));
        Assert.assertEquals(0, dataStorage1.getMap().size());
    }

    @Test
    public void testExpirableData() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        ProtectedExpirableDataStorage.CHECK_TTL_INTERVAL = 10;
        // CHECK_TTL_INTERVAL is used in constructor of ProtectedExpirableDataStorage so we recreate it here
        dataStorage1 = new ProtectedExpirableDataStorage(peerGroup1, new File("dummy"));
        mockData.ttl = 50;

        ProtectedData data = dataStorage1.getDataWithSignedSeqNr(mockData, storageSignatureKeyPair1);
        Assert.assertTrue(dataStorage1.add(data, null));
        Thread.sleep(5);
        Assert.assertEquals(1, dataStorage1.getMap().size());
        // still there 
        Thread.sleep(20);
        Assert.assertEquals(1, dataStorage1.getMap().size());

        Thread.sleep(40);
        // now should be removed
        Assert.assertEquals(0, dataStorage1.getMap().size());

        // add with date in future
        data = dataStorage1.getDataWithSignedSeqNr(mockData, storageSignatureKeyPair1);
        int newSequenceNumber = data.sequenceNumber + 1;
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        byte[] signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        ProtectedData dataWithFutureDate = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        dataWithFutureDate.date = new Date(new Date().getTime() + 60 * 60 * sleepTime);
        // force serialisation (date check is done in readObject)
        ProtectedData newData = Utilities.deserialize(Utilities.serialize(dataWithFutureDate));
        Assert.assertTrue(dataStorage1.add(newData, null));
        Thread.sleep(5);
        Assert.assertEquals(1, dataStorage1.getMap().size());
        Thread.sleep(50);
        Assert.assertEquals(0, dataStorage1.getMap().size());
    }

    @Test
    public void testMultiAddRemoveProtectedData() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        MockData mockData = new MockData("msg1", keyRing1.getSignatureKeyPair().getPublic());
        ProtectedData data = dataStorage1.getDataWithSignedSeqNr(mockData, storageSignatureKeyPair1);
        Assert.assertTrue(dataStorage1.add(data, null));

        // remove with not updated seq nr -> failure
        int newSequenceNumber = 0;
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        byte[] signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        ProtectedData dataToRemove = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertFalse(dataStorage1.remove(dataToRemove, null));

        // remove with too high updated seq nr -> ok
        newSequenceNumber = 2;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertTrue(dataStorage1.remove(dataToRemove, null));

        // add to empty map, any seq nr. -> ok
        newSequenceNumber = 2;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        ProtectedData dataToAdd = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertTrue(dataStorage1.add(dataToAdd, null));

        // add with updated seq nr below previous -> failure
        newSequenceNumber = 1;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToAdd = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertFalse(dataStorage1.add(dataToAdd, null));

        // add with updated seq nr over previous -> ok
        newSequenceNumber = 3;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToAdd = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertTrue(dataStorage1.add(dataToAdd, null));

        // add with same seq nr  -> failure
        newSequenceNumber = 3;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToAdd = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertFalse(dataStorage1.add(dataToAdd, null));

        // add with same data but higher seq nr.  -> ok, ignore
        newSequenceNumber = 4;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToAdd = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertTrue(dataStorage1.add(dataToAdd, null));

        // remove with with same seq nr as prev. ignored -> failed
        newSequenceNumber = 4;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertFalse(dataStorage1.remove(dataToRemove, null));

        // remove with with higher seq nr -> ok
        newSequenceNumber = 5;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertTrue(dataStorage1.remove(dataToRemove, null));
    }

    @Test
    public void testAddAndRemoveMailboxData() throws InterruptedException, NoSuchAlgorithmException, CertificateException,
            KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        // sender 
        MockMessage mockMessage = new MockMessage("MockMessage");
        SealedAndSignedMessage sealedAndSignedMessage = new SealedAndSignedMessage(encryptionService1.encryptAndSign(keyRing1.getPubKeyRing(), mockMessage), Hash.getHash("aa"));
        ExpirableMailboxPayload expirableMailboxPayload = new ExpirableMailboxPayload(sealedAndSignedMessage,
                keyRing1.getSignatureKeyPair().getPublic(),
                keyRing2.getSignatureKeyPair().getPublic());

        ProtectedMailboxData data = dataStorage1.getMailboxDataWithSignedSeqNr(expirableMailboxPayload, storageSignatureKeyPair1, storageSignatureKeyPair2.getPublic());
        Assert.assertTrue(dataStorage1.add(data, null));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, dataStorage1.getMap().size());

        // receiver (storageSignatureKeyPair2)
        int newSequenceNumber = data.sequenceNumber + 1;
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));

        byte[] signature;
        ProtectedMailboxData dataToRemove;

        // wrong sig -> fail
        signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, storageSignatureKeyPair2.getPublic(), newSequenceNumber, signature, storageSignatureKeyPair2.getPublic());
        Assert.assertFalse(dataStorage1.removeMailboxData(dataToRemove, null));

        // wrong seq nr
        signature = Sig.sign(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, storageSignatureKeyPair2.getPublic(), data.sequenceNumber, signature, storageSignatureKeyPair2.getPublic());
        Assert.assertFalse(dataStorage1.removeMailboxData(dataToRemove, null));

        // wrong signingKey
        signature = Sig.sign(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature, storageSignatureKeyPair2.getPublic());
        Assert.assertFalse(dataStorage1.removeMailboxData(dataToRemove, null));

        // wrong peerPubKey
        signature = Sig.sign(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, storageSignatureKeyPair2.getPublic(), newSequenceNumber, signature, storageSignatureKeyPair1.getPublic());
        Assert.assertFalse(dataStorage1.removeMailboxData(dataToRemove, null));

        // receiver can remove it (storageSignatureKeyPair2) -> all ok
        Assert.assertEquals(1, dataStorage1.getMap().size());
        signature = Sig.sign(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, storageSignatureKeyPair2.getPublic(), newSequenceNumber, signature, storageSignatureKeyPair2.getPublic());
        Assert.assertTrue(dataStorage1.removeMailboxData(dataToRemove, null));

        Assert.assertEquals(0, dataStorage1.getMap().size());
    }


    /*@Test
    public void testTryToHack() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        ProtectedData data = dataStorage1.getDataWithSignedSeqNr(mockData, storageSignatureKeyPair1);
        Assert.assertTrue(dataStorage1.add(data, null));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, dataStorage1.getMap().size());
        Assert.assertEquals(1, dataStorage2.getMap().size());

        // hackers key pair is storageSignatureKeyPair2
        // change seq nr. and signature: fails on both own and peers dataStorage
        int newSequenceNumber = data.sequenceNumber + 1;
        byte[] hashOfDataAndSeqNr = cryptoService2.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        byte[] signature = cryptoService2.signStorageData(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        ProtectedData dataToAdd = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertFalse(dataStorage1.add(dataToAdd, null));
        Assert.assertFalse(dataStorage2.add(dataToAdd, null));

        // change seq nr. and signature and data pub key. fails on peers dataStorage, succeeds on own dataStorage 
        newSequenceNumber = data.sequenceNumber + 2;
        hashOfDataAndSeqNr = cryptoService2.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = cryptoService2.signStorageData(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToAdd = new ProtectedData(data.expirablePayload, data.ttl, storageSignatureKeyPair2.getPublic(), newSequenceNumber, signature);
        Assert.assertTrue(dataStorage2.add(dataToAdd, null));
        Assert.assertFalse(dataStorage1.add(dataToAdd, null));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, dataStorage2.getMap().size());
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, dataStorage1.getMap().size());
        Assert.assertEquals(data, dataStorage1.getMap().values().stream().findFirst().get());
        Assert.assertEquals(dataToAdd, dataStorage2.getMap().values().stream().findFirst().get());
        Assert.assertNotEquals(data, dataToAdd);

        newSequenceNumber = data.sequenceNumber + 3;
        hashOfDataAndSeqNr = cryptoService1.getHash(new DataAndSeqNr(data.expirablePayload, newSequenceNumber));
        signature = cryptoService1.signStorageData(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        ProtectedData dataToRemove = new ProtectedData(data.expirablePayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature);
        Assert.assertTrue(dataStorage1.remove(dataToRemove, null));
        Assert.assertEquals(0, dataStorage1.getMap().size());
    }*/

   /* //@Test
    public void testTryToHackMailboxData() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        MockMessage mockMessage = new MockMessage("MockMessage");
        SealedAndSignedMessage sealedAndSignedMessage = cryptoService1.encryptAndSignMessage(keyRing1.getPubKeyRing(), mockMessage);
        ExpirableMailboxPayload expirableMailboxPayload = new ExpirableMailboxPayload(sealedAndSignedMessage,
                keyRing1.getStorageSignatureKeyPair().getPublic(),
                keyRing2.getStorageSignatureKeyPair().getPublic());

        // sender 
        ProtectedMailboxData data = dataStorage1.getMailboxDataWithSignedSeqNr(expirableMailboxPayload, storageSignatureKeyPair1, storageSignatureKeyPair2.getPublic());
        Assert.assertTrue(dataStorage1.add(data, null));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, dataStorage1.getMap().size());

        // receiver (storageSignatureKeyPair2)
        int newSequenceNumber = data.sequenceNumber + 1;
        byte[] hashOfDataAndSeqNr = cryptoService2.getHash(new DataAndSeqNr(expirableMailboxPayload, newSequenceNumber));

        byte[] signature;
        ProtectedMailboxData dataToRemove;

        // wrong sig -> fail
        signature = cryptoService2.signStorageData(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, storageSignatureKeyPair2.getPublic(), newSequenceNumber, signature, storageSignatureKeyPair2.getPublic());
        Assert.assertFalse(dataStorage1.removeMailboxData(dataToRemove, null));

        // wrong seq nr
        signature = cryptoService2.signStorageData(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, storageSignatureKeyPair2.getPublic(), data.sequenceNumber, signature, storageSignatureKeyPair2.getPublic());
        Assert.assertFalse(dataStorage1.removeMailboxData(dataToRemove, null));

        // wrong signingKey
        signature = cryptoService2.signStorageData(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, data.ownerStoragePubKey, newSequenceNumber, signature, storageSignatureKeyPair2.getPublic());
        Assert.assertFalse(dataStorage1.removeMailboxData(dataToRemove, null));

        // wrong peerPubKey
        signature = cryptoService2.signStorageData(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, storageSignatureKeyPair2.getPublic(), newSequenceNumber, signature, storageSignatureKeyPair1.getPublic());
        Assert.assertFalse(dataStorage1.removeMailboxData(dataToRemove, null));

        // all ok
        Assert.assertEquals(1, dataStorage1.getMap().size());
        signature = cryptoService2.signStorageData(storageSignatureKeyPair2.getPrivate(), hashOfDataAndSeqNr);
        dataToRemove = new ProtectedMailboxData(expirableMailboxPayload, data.ttl, storageSignatureKeyPair2.getPublic(), newSequenceNumber, signature, storageSignatureKeyPair2.getPublic());
        Assert.assertTrue(dataStorage1.removeMailboxData(dataToRemove, null));

        Assert.assertEquals(0, dataStorage1.getMap().size());
    }
*/
}

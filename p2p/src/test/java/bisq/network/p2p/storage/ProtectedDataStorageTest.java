/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage;

import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.mocks.MockData;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.UserThread;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.Sig;
import bisq.common.storage.FileUtil;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateException;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ProtectedDataStorageTest {
    private static final Logger log = LoggerFactory.getLogger(ProtectedDataStorageTest.class);

    final boolean useClearNet = true;
    private final Set<NodeAddress> seedNodes = new HashSet<>();
    private NetworkNode networkNode1;
    private PeerManager peerManager1;
    private EncryptionService encryptionService1, encryptionService2;
    private P2PDataStorage dataStorage1;
    private KeyPair storageSignatureKeyPair1, storageSignatureKeyPair2;
    private KeyRing keyRing1, keyRing2;
    private MockData mockData;
    private final int sleepTime = 100;
    private File dir1;
    private File dir2;

    @Before
    public void setup() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Security.addProvider(new BouncyCastleProvider());
        dir1 = File.createTempFile("temp_tests1", "");
        //noinspection ResultOfMethodCallIgnored,ResultOfMethodCallIgnored
        dir1.delete();
        //noinspection ResultOfMethodCallIgnored,ResultOfMethodCallIgnored
        dir1.mkdir();
        dir2 = File.createTempFile("temp_tests2", "");
        //noinspection ResultOfMethodCallIgnored,ResultOfMethodCallIgnored
        dir2.delete();
        //noinspection ResultOfMethodCallIgnored,ResultOfMethodCallIgnored
        dir2.mkdir();

        UserThread.setExecutor(Executors.newSingleThreadExecutor());
        P2PDataStorage.CHECK_TTL_INTERVAL_SEC = 500;

        keyRing1 = new KeyRing(new KeyStorage(dir1));

        storageSignatureKeyPair1 = keyRing1.getSignatureKeyPair();
        encryptionService1 = new EncryptionService(keyRing1, TestUtils.getNetworkProtoResolver());
        P2PService p2PService = TestUtils.getAndStartSeedNode(8001, useClearNet, seedNodes).getSeedNodeP2PService();
        networkNode1 = p2PService.getNetworkNode();
        peerManager1 = p2PService.getPeerManager();
        dataStorage1 = p2PService.getP2PDataStorage();

        // for mailbox
        keyRing2 = new KeyRing(new KeyStorage(dir2));
        storageSignatureKeyPair2 = keyRing2.getSignatureKeyPair();
        encryptionService2 = new EncryptionService(keyRing2, TestUtils.getNetworkProtoResolver());

        mockData = new MockData("mockData", keyRing1.getSignatureKeyPair().getPublic());
        Thread.sleep(sleepTime);
    }

    @After
    public void tearDown() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException {
        Thread.sleep(sleepTime);
        if (dataStorage1 != null) dataStorage1.shutDown();
        if (peerManager1 != null) peerManager1.shutDown();

        if (networkNode1 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            networkNode1.shutDown(shutDownLatch::countDown);
            shutDownLatch.await();
        }

        Path path = Paths.get(TestUtils.test_dummy_dir);
        File dir = path.toFile();
        FileUtil.deleteDirectory(dir);
        FileUtil.deleteDirectory(dir1);
        FileUtil.deleteDirectory(dir2);
    }

    //@Test
    public void testAddAndRemove() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        ProtectedStorageEntry data = dataStorage1.getProtectedStorageEntry(mockData, storageSignatureKeyPair1);
        Assert.assertTrue(dataStorage1.addProtectedStorageEntry(data, null, null, true));
        Assert.assertEquals(1, dataStorage1.getMap().size());

        int newSequenceNumber = data.getSequenceNumber() + 1;
        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(data.getProtectedStoragePayload(), newSequenceNumber));
        byte[] signature = Sig.sign(storageSignatureKeyPair1.getPrivate(), hashOfDataAndSeqNr);
        ProtectedStorageEntry dataToRemove = new ProtectedStorageEntry(data.getProtectedStoragePayload(), data.getOwnerPubKey(), newSequenceNumber, signature);
        Assert.assertTrue(dataStorage1.remove(dataToRemove, null, true));
        Assert.assertEquals(0, dataStorage1.getMap().size());
    }

    // @Test
    public void testTTL() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        mockData.ttl = (int) (P2PDataStorage.CHECK_TTL_INTERVAL_SEC * 1.5);
        ProtectedStorageEntry data = dataStorage1.getProtectedStorageEntry(mockData, storageSignatureKeyPair1);
        log.debug("data.date " + data.getCreationTimeStamp());
        Assert.assertTrue(dataStorage1.addProtectedStorageEntry(data, null, null, true));
        log.debug("test 1");
        Assert.assertEquals(1, dataStorage1.getMap().size());

        Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_SEC);
        log.debug("test 2");
        Assert.assertEquals(1, dataStorage1.getMap().size());

        Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_SEC * 2);
        log.debug("test 3 removed");
        Assert.assertEquals(0, dataStorage1.getMap().size());
    }

    /* //@Test
     public void testRePublish() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
         mockData.ttl = (int) (P2PDataStorage.CHECK_TTL_INTERVAL_MILLIS * 1.5);
         ProtectedStorageEntry data = dataStorage1.getDataWithSignedSeqNr(mockData, storageSignatureKeyPair1);
         Assert.assertTrue(dataStorage1.add(data, null));
         Assert.assertEquals(1, dataStorage1.getMap().size());
         Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_MILLIS);
         log.debug("test 1");
         Assert.assertEquals(1, dataStorage1.getMap().size());

         data = dataStorage1.getDataWithSignedSeqNr(mockData, storageSignatureKeyPair1);
         Assert.assertTrue(dataStorage1.rePublish(data, null));
         Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_MILLIS);
         log.debug("test 2");
         Assert.assertEquals(1, dataStorage1.getMap().size());

         data = dataStorage1.getDataWithSignedSeqNr(mockData, storageSignatureKeyPair1);
         Assert.assertTrue(dataStorage1.rePublish(data, null));
         Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_MILLIS);
         log.debug("test 3");
         Assert.assertEquals(1, dataStorage1.getMap().size());

         Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_MILLIS);
         log.debug("test 4");
         Assert.assertEquals(1, dataStorage1.getMap().size());

         Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_MILLIS * 2);
         log.debug("test 5 removed");
         Assert.assertEquals(0, dataStorage1.getMap().size());
     }
 */
    @Test
    public void testRefreshTTL() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        mockData.ttl = (int) (P2PDataStorage.CHECK_TTL_INTERVAL_SEC * 1.5);
        ProtectedStorageEntry data = dataStorage1.getProtectedStorageEntry(mockData, storageSignatureKeyPair1);
        Assert.assertTrue(dataStorage1.addProtectedStorageEntry(data, null, null, true));
        Assert.assertEquals(1, dataStorage1.getMap().size());
        Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_SEC);
        log.debug("test 1");
        Assert.assertEquals(1, dataStorage1.getMap().size());

        RefreshOfferMessage refreshTTLMessage = dataStorage1.getRefreshTTLMessage(mockData, storageSignatureKeyPair1);
        Assert.assertTrue(dataStorage1.refreshTTL(refreshTTLMessage, null, true));
        Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_SEC);
        log.debug("test 2");
        Assert.assertEquals(1, dataStorage1.getMap().size());

        refreshTTLMessage = dataStorage1.getRefreshTTLMessage(mockData, storageSignatureKeyPair1);
        Assert.assertTrue(dataStorage1.refreshTTL(refreshTTLMessage, null, true));
        Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_SEC);
        log.debug("test 3");
        Assert.assertEquals(1, dataStorage1.getMap().size());

        Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_SEC);
        log.debug("test 4");
        Assert.assertEquals(1, dataStorage1.getMap().size());

        Thread.sleep(P2PDataStorage.CHECK_TTL_INTERVAL_SEC * 2);
        log.debug("test 5 removed");
        Assert.assertEquals(0, dataStorage1.getMap().size());
    }
}

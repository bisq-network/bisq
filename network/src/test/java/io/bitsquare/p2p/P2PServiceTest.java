package io.bitsquare.p2p;

import io.bitsquare.common.crypto.*;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.crypto.SealedAndSignedMessage;
import io.bitsquare.p2p.messaging.DecryptedMsgWithPubKey;
import io.bitsquare.p2p.messaging.MailboxMessage;
import io.bitsquare.p2p.messaging.SendMailboxMessageListener;
import io.bitsquare.p2p.mocks.MockMailboxMessage;
import io.bitsquare.p2p.network.LocalhostNetworkNode;
import io.bitsquare.p2p.routing.Routing;
import io.bitsquare.p2p.seed.SeedNode;
import io.bitsquare.p2p.storage.data.DataAndSeqNr;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.p2p.storage.mocks.MockData;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

// TorNode created. Took 6 sec.
// Hidden service created. Took 40-50 sec.
// Connection establishment takes about 4 sec.

// need to define seed node addresses first before using tor version
@Ignore
public class P2PServiceTest {
    private static final Logger log = LoggerFactory.getLogger(P2PServiceTest.class);

    boolean useLocalhost = true;
    private ArrayList<Address> seedNodes;
    private int sleepTime;
    private KeyRing keyRing1, keyRing2, keyRing3;
    private EncryptionService encryptionService1, encryptionService2, encryptionService3;
    private P2PService p2PService1, p2PService2, p2PService3;
    private SeedNode seedNode1, seedNode2, seedNode3;

    @Before
    public void setup() throws InterruptedException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        LocalhostNetworkNode.setSimulateTorDelayTorNode(10);
        LocalhostNetworkNode.setSimulateTorDelayHiddenService(100);
        Routing.setMaxConnections(8);

        keyRing1 = new KeyRing(new KeyStorage(new File("temp_keyStorage1")));
        keyRing2 = new KeyRing(new KeyStorage(new File("temp_keyStorage2")));
        keyRing3 = new KeyRing(new KeyStorage(new File("temp_keyStorage3")));
        encryptionService1 = new EncryptionService(keyRing1);
        encryptionService2 = new EncryptionService(keyRing2);
        encryptionService3 = new EncryptionService(keyRing3);

        seedNodes = new ArrayList<>();
        if (useLocalhost) {
            seedNodes.add(new Address("localhost:8001"));
            seedNodes.add(new Address("localhost:8002"));
            seedNodes.add(new Address("localhost:8003"));
            sleepTime = 100;

        } else {
            seedNodes.add(new Address("3omjuxn7z73pxoee.onion:8001"));
            seedNodes.add(new Address("j24fxqyghjetgpdx.onion:8002"));
            seedNodes.add(new Address("45367tl6unwec6kw.onion:8003"));
            sleepTime = 1000;
        }

        seedNode1 = TestUtils.getAndStartSeedNode(8001, encryptionService1, keyRing1, useLocalhost, seedNodes);
        p2PService1 = seedNode1.getP2PService();
        p2PService2 = TestUtils.getAndAuthenticateP2PService(8002, encryptionService2, keyRing2, useLocalhost, seedNodes);
    }

    @After
    public void tearDown() throws InterruptedException {
        Thread.sleep(sleepTime);

        if (seedNode1 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            seedNode1.shutDown(() -> shutDownLatch.countDown());
            shutDownLatch.await();
        }
        if (seedNode2 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            seedNode2.shutDown(() -> shutDownLatch.countDown());
            shutDownLatch.await();
        }
        if (seedNode3 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            seedNode3.shutDown(() -> shutDownLatch.countDown());
            shutDownLatch.await();
        }
        if (p2PService1 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            p2PService1.shutDown(() -> shutDownLatch.countDown());
            shutDownLatch.await();
        }
        if (p2PService2 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            p2PService2.shutDown(() -> shutDownLatch.countDown());
            shutDownLatch.await();
        }
        if (p2PService3 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            p2PService3.shutDown(() -> shutDownLatch.countDown());
            shutDownLatch.await();
        }
    }

    @Test
    public void testAdversaryAttacks() throws InterruptedException, NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, CryptoException, SignatureException, InvalidKeyException, NoSuchProviderException {
        p2PService3 = TestUtils.getAndAuthenticateP2PService(8003, encryptionService3, keyRing3, useLocalhost, seedNodes);

        MockData origData = new MockData("mockData1", keyRing1.getSignatureKeyPair().getPublic());

        p2PService1.addData(origData);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());


        // p2PService3 is adversary 
        KeyPair msgSignatureKeyPairAdversary = keyRing3.getSignatureKeyPair();

        // try to remove data -> fails
        Assert.assertFalse(p2PService3.removeData(origData));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());


        // try to add manipulated data -> fails
        Assert.assertFalse(p2PService3.removeData(new MockData("mockData2", origData.publicKey)));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());

        // try to manipulate seq nr. -> fails
        ProtectedData origProtectedData = p2PService3.getDataMap().values().stream().findFirst().get();
        ProtectedData protectedDataManipulated = new ProtectedData(origProtectedData.expirablePayload, origProtectedData.ttl, origProtectedData.ownerStoragePubKey, origProtectedData.sequenceNumber + 1, origProtectedData.signature);
        Assert.assertFalse(p2PService3.removeData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());

        // try to manipulate seq nr. + pubKey -> fails
        protectedDataManipulated = new ProtectedData(origProtectedData.expirablePayload, origProtectedData.ttl, msgSignatureKeyPairAdversary.getPublic(), origProtectedData.sequenceNumber + 1, origProtectedData.signature);
        Assert.assertFalse(p2PService3.removeData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());

        // try to manipulate seq nr. + pubKey + sig -> fails
        int sequenceNumberManipulated = origProtectedData.sequenceNumber + 1;
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(origProtectedData.expirablePayload, sequenceNumberManipulated));
        byte[] signature = Sig.sign(msgSignatureKeyPairAdversary.getPrivate(), hashOfDataAndSeqNr);
        protectedDataManipulated = new ProtectedData(origProtectedData.expirablePayload, origProtectedData.ttl, msgSignatureKeyPairAdversary.getPublic(), sequenceNumberManipulated, signature);
        Assert.assertFalse(p2PService3.removeData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());


        // data owner removes -> ok
        Assert.assertTrue(p2PService1.removeData(origData));
        Thread.sleep(sleepTime);
        Assert.assertEquals(0, p2PService1.getDataMap().size());
        Assert.assertEquals(0, p2PService2.getDataMap().size());
        Assert.assertEquals(0, p2PService3.getDataMap().size());

        // adversary manage to change data before it is broadcasted to others
        // data owner is connected only to adversary -> he change data at onMessage and broadcast manipulated data
        // first he tries to use the orig. pubKey in the data -> fails as pub keys not matching
        MockData manipulatedData = new MockData("mockData1_manipulated", origData.publicKey);
        sequenceNumberManipulated = 0;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(manipulatedData, sequenceNumberManipulated));
        signature = Sig.sign(msgSignatureKeyPairAdversary.getPrivate(), hashOfDataAndSeqNr);
        protectedDataManipulated = new ProtectedData(origProtectedData.expirablePayload, origProtectedData.ttl, msgSignatureKeyPairAdversary.getPublic(), sequenceNumberManipulated, signature);
        Assert.assertFalse(p2PService3.addData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(0, p2PService1.getDataMap().size());
        Assert.assertEquals(0, p2PService2.getDataMap().size());
        Assert.assertEquals(0, p2PService3.getDataMap().size());

        // then he tries to use his pubKey but orig data payload -> fails as pub keys nto matching
        manipulatedData = new MockData("mockData1_manipulated", msgSignatureKeyPairAdversary.getPublic());
        sequenceNumberManipulated = 0;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(manipulatedData, sequenceNumberManipulated));
        signature = Sig.sign(msgSignatureKeyPairAdversary.getPrivate(), hashOfDataAndSeqNr);
        protectedDataManipulated = new ProtectedData(origProtectedData.expirablePayload, origProtectedData.ttl, msgSignatureKeyPairAdversary.getPublic(), sequenceNumberManipulated, signature);
        Assert.assertFalse(p2PService3.addData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(0, p2PService1.getDataMap().size());
        Assert.assertEquals(0, p2PService2.getDataMap().size());
        Assert.assertEquals(0, p2PService3.getDataMap().size());

        // then he tries to use his pubKey -> now he succeeds, but its same as adding a completely new msg and 
        // payload data has adversary's pubKey so he could hijack the owners data
        manipulatedData = new MockData("mockData1_manipulated", msgSignatureKeyPairAdversary.getPublic());
        sequenceNumberManipulated = 0;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(manipulatedData, sequenceNumberManipulated));
        signature = Sig.sign(msgSignatureKeyPairAdversary.getPrivate(), hashOfDataAndSeqNr);
        protectedDataManipulated = new ProtectedData(manipulatedData, origProtectedData.ttl, msgSignatureKeyPairAdversary.getPublic(), sequenceNumberManipulated, signature);
        Assert.assertTrue(p2PService3.addData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());

        // let clean up. he can remove his own data
        Assert.assertTrue(p2PService3.removeData(manipulatedData));
        Thread.sleep(sleepTime);
        Assert.assertEquals(0, p2PService1.getDataMap().size());
        Assert.assertEquals(0, p2PService2.getDataMap().size());
        Assert.assertEquals(0, p2PService3.getDataMap().size());


        // finally he tries both previous attempts with same data - > same as before
        manipulatedData = new MockData("mockData1", origData.publicKey);
        sequenceNumberManipulated = 0;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(manipulatedData, sequenceNumberManipulated));
        signature = Sig.sign(msgSignatureKeyPairAdversary.getPrivate(), hashOfDataAndSeqNr);
        protectedDataManipulated = new ProtectedData(origProtectedData.expirablePayload, origProtectedData.ttl, msgSignatureKeyPairAdversary.getPublic(), sequenceNumberManipulated, signature);
        Assert.assertFalse(p2PService3.addData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(0, p2PService1.getDataMap().size());
        Assert.assertEquals(0, p2PService2.getDataMap().size());
        Assert.assertEquals(0, p2PService3.getDataMap().size());

        manipulatedData = new MockData("mockData1", msgSignatureKeyPairAdversary.getPublic());
        sequenceNumberManipulated = 0;
        hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(manipulatedData, sequenceNumberManipulated));
        signature = Sig.sign(msgSignatureKeyPairAdversary.getPrivate(), hashOfDataAndSeqNr);
        protectedDataManipulated = new ProtectedData(manipulatedData, origProtectedData.ttl, msgSignatureKeyPairAdversary.getPublic(), sequenceNumberManipulated, signature);
        Assert.assertTrue(p2PService3.addData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());

        // lets reset map
        Assert.assertTrue(p2PService3.removeData(protectedDataManipulated.expirablePayload));
        Thread.sleep(sleepTime);
        Assert.assertEquals(0, p2PService1.getDataMap().size());
        Assert.assertEquals(0, p2PService2.getDataMap().size());
        Assert.assertEquals(0, p2PService3.getDataMap().size());

        // owner can add any time his data
        Assert.assertTrue(p2PService1.addData(origData));
        Thread.sleep(sleepTime);
        Assert.assertEquals(1, p2PService1.getDataMap().size());
        Assert.assertEquals(1, p2PService2.getDataMap().size());
        Assert.assertEquals(1, p2PService3.getDataMap().size());
    }

    //@Test
    public void testSendMailboxMessageToOnlinePeer() throws InterruptedException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        LocalhostNetworkNode.setSimulateTorDelayTorNode(0);
        LocalhostNetworkNode.setSimulateTorDelayHiddenService(0);

        // send to online peer
        CountDownLatch latch2 = new CountDownLatch(2);
        MockMailboxMessage mockMessage = new MockMailboxMessage("MockMailboxMessage", p2PService2.getAddress());
        p2PService2.addMessageListener((message, connection) -> {
            log.trace("message " + message);
            if (message instanceof SealedAndSignedMessage) {
                try {
                    SealedAndSignedMessage sealedAndSignedMessage = (SealedAndSignedMessage) message;
                    DecryptedMsgWithPubKey decryptedMsgWithPubKey = encryptionService2.decryptAndVerify(sealedAndSignedMessage.sealedAndSigned);
                    Assert.assertEquals(mockMessage, decryptedMsgWithPubKey.message);
                    Assert.assertEquals(p2PService2.getAddress(), ((MailboxMessage) decryptedMsgWithPubKey.message).getSenderAddress());
                    latch2.countDown();
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
            }
        });

        p2PService1.sendEncryptedMailboxMessage(
                p2PService2.getAddress(),
                keyRing2.getPubKeyRing(),
                mockMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.trace("Message arrived at peer.");
                        latch2.countDown();
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.trace("Message stored in mailbox.");
                    }

                    @Override
                    public void onFault() {
                        log.error("onFault");
                    }
                }
        );
        latch2.await();
    }

    //@Test
    public void testSendMailboxMessageToOfflinePeer() throws InterruptedException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        LocalhostNetworkNode.setSimulateTorDelayTorNode(0);
        LocalhostNetworkNode.setSimulateTorDelayHiddenService(0);

        // send msg to offline peer
        MockMailboxMessage mockMessage = new MockMailboxMessage(
                "MockMailboxMessage",
                p2PService2.getAddress()
        );
        CountDownLatch latch2 = new CountDownLatch(1);
        p2PService2.sendEncryptedMailboxMessage(
                new Address("localhost:8003"),
                keyRing3.getPubKeyRing(),
                mockMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.trace("Message arrived at peer.");
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.trace("Message stored in mailbox.");
                        latch2.countDown();
                    }

                    @Override
                    public void onFault() {
                        log.error("onFault");
                    }
                }
        );
        latch2.await();
        Thread.sleep(2000);


        // start node 3
        p2PService3 = TestUtils.getAndAuthenticateP2PService(8003, encryptionService3, keyRing3, useLocalhost, seedNodes);
        Thread.sleep(sleepTime);
        CountDownLatch latch3 = new CountDownLatch(1);
        p2PService3.addDecryptedMailboxListener((decryptedMessageWithPubKey, senderAddress) -> {
            log.debug("decryptedMessageWithPubKey " + decryptedMessageWithPubKey.toString());
            Assert.assertEquals(mockMessage, decryptedMessageWithPubKey.message);
            Assert.assertEquals(p2PService2.getAddress(), ((MailboxMessage) decryptedMessageWithPubKey.message).getSenderAddress());
            latch3.countDown();
        });
        latch3.await();
    }
}

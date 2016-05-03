package io.bitsquare.p2p.network;

import io.bitsquare.app.Version;
import io.bitsquare.common.Clock;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.KeyStorage;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.Utils;
import io.bitsquare.p2p.messaging.DirectMessage;
import io.bitsquare.p2p.messaging.SendDirectMessageListener;
import io.bitsquare.p2p.seed.SeedNode;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.Security;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NetworkStressTest {
    // Test parameters

    /** Whether to log messages less important than warnings. */
    private static final boolean USE_DETAILED_LOGGING = false;

    // Constants

    /** Environment variable to specify the number of peers in the test. */
    private static final String NPEERS_ENVVAR = "STRESS_TEST_NPEERS";
    /** Environment variable to specify a persistent test data directory. */
    private static final String TEST_DIR_ENVVAR = "STRESS_TEST_DIR";
    /** Environment variable to specify the number of direct messages sent per peer. */
    private static final String DIRECT_COUNT_ENVVAR = "STRESS_TEST_NDIRECT";

    /** Numeric identifier of the regtest Bitcoin network. */
    private static final int REGTEST_NETWORK_ID = 2;

    /** Default number of peers in the test. */
    private static final int NPEERS_DEFAULT = 4;
    /** Minimum number of peers for the test to work. */
    private static final int NPEERS_MIN = 2;
    /** Default number of direct messages to be sent by each peer. */
    private static final int DIRECT_COUNT_DEFAULT = 100;

    /** Minimum delay between direct messages in milliseconds, 25% larger than throttle limit. */
    private static long MIN_DIRECT_DELAY_MILLIS = Math.round(1.25 * (1.0 / Connection.MSG_THROTTLE_PER_SEC) * 1000);
    /** Maximum delay between direct messages in milliseconds, 10 times larger than minimum. */
    private static long MAX_DIRECT_DELAY_MILLIS = 10 * MIN_DIRECT_DELAY_MILLIS;

    // Instance fields

    /** A directory to (temporarily) hold seed and normal nodes' configuration and state files. */
    private Path testDataDir;
    /** A single seed node that other nodes will contact to request initial data. */
    private SeedNode seedNode;
    /** A list of peer nodes represented as P2P services. */
    private List<P2PService> peerNodes = new ArrayList<>();
    /** A list of peer node's public key rings. */
    private List<PubKeyRing> peerPKRings = new ArrayList<>();

    /** A barrier to wait for concurrent reception of preliminary data in peers. */
    private CountDownLatch prelimDataLatch;
    /** A barrier to wait for concurrent bootstrap of peers. */
    private CountDownLatch bootstrapLatch;

    /** Number of direct messages to be sent by each peer. */
    private int directCount = DIRECT_COUNT_DEFAULT;

    @Before
    public void setUp() throws Exception {
        // Parse test parameter environment variables.

        /** Number of peer nodes to create. */
        int nPeers = NPEERS_DEFAULT;
        final String nPeersEnv = System.getenv(NPEERS_ENVVAR);
        if (nPeersEnv != null && !nPeersEnv.equals(""))
            nPeers = Integer.parseInt(nPeersEnv);
        if (nPeers < NPEERS_MIN)
            throw new IllegalArgumentException(
                    String.format("Test needs at least %d peer nodes to work: %d", NPEERS_MIN, nPeers)
            );

        final String nDirectEnv = System.getenv(DIRECT_COUNT_ENVVAR);
        if (nDirectEnv != null && !nDirectEnv.equals(""))
            directCount = Integer.parseInt(nDirectEnv);
        if (directCount < 0)
            throw new IllegalArgumentException(
                    String.format("Direct messages sent per peer must not be negative: %d", directCount)
            );

        prelimDataLatch = new CountDownLatch(nPeers);
        bootstrapLatch = new CountDownLatch(nPeers);

        /** A property where threads can indicate setup failure of local services (Tor node, hidden service). */
        final BooleanProperty localServicesFailed = new SimpleBooleanProperty(false);
        /** A barrier to wait for concurrent setup of local services (Tor node, hidden service). */
        final CountDownLatch localServicesLatch = new CountDownLatch(1 /*seed node*/ + nPeers);

        // Set a security provider to allow key generation.
        Security.addProvider(new BouncyCastleProvider());

        // Create the test data directory.
        testDataDir = createTestDataDirectory();

        // Create and start the seed node.
        seedNode = new SeedNode(testDataDir.toString());
        final NodeAddress seedNodeAddress = getSeedNodeAddress();
        final boolean useLocalhost = seedNodeAddress.hostName.equals("localhost");
        final Set<NodeAddress> seedNodes = new HashSet<>(1);
        seedNodes.add(seedNodeAddress);  // the only seed node in tests
        seedNode.createAndStartP2PService(seedNodeAddress, useLocalhost,
                REGTEST_NETWORK_ID, USE_DETAILED_LOGGING, seedNodes,
                new SeedServiceListener(localServicesLatch, localServicesFailed));

        // Create and start peer nodes.
        SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (useLocalhost) {
            seedNodesRepository.setLocalhostSeedNodeAddresses(seedNodes);
        } else {
            seedNodesRepository.setTorSeedNodeAddresses(seedNodes);
        }
        for (int p = 0; p < nPeers; p++) {
            final int peerPort = Utils.findFreeSystemPort();
            final File peerDir = new File(testDataDir.toFile(), String.format("peer-%06d", p));
            final File peerTorDir = new File(peerDir, "tor");
            final File peerStorageDir = new File(peerDir, "db");
            final File peerKeysDir = new File(peerDir, "keys");
            //noinspection ResultOfMethodCallIgnored
            peerKeysDir.mkdirs();  // needed for creating the key ring
            final KeyStorage peerKeyStorage = new KeyStorage(peerKeysDir);
            final KeyRing peerKeyRing = new KeyRing(peerKeyStorage);
            peerPKRings.add(peerKeyRing.getPubKeyRing());
            final EncryptionService peerEncryptionService = new EncryptionService(peerKeyRing);
            final P2PService peer = new P2PService(seedNodesRepository, peerPort, peerTorDir, useLocalhost,
                    REGTEST_NETWORK_ID, peerStorageDir, new Clock(), peerEncryptionService, peerKeyRing);
            peerNodes.add(peer);
            peer.start(new PeerServiceListener(localServicesLatch, localServicesFailed));
        }

        // Wait for concurrent tasks to finish.
        localServicesLatch.await();

        // Check if any node reported setup failure on start.
        if (localServicesFailed.get()) {
            throw new Exception("nodes failed to start");
        }
    }

    @NotNull
    private static NodeAddress getSeedNodeAddress() {
        // The address is only considered by ``SeedNodesRepository`` if
        // it ends in the digit matching the network identifier.
        int port;
        do {
            port = Utils.findFreeSystemPort();
        } while (port % 10 != REGTEST_NETWORK_ID);
        return new NodeAddress("localhost", port);
    }

    @After
    public void tearDown() throws InterruptedException, IOException {
        /** A barrier to wait for concurrent shutdown of services. */
        final CountDownLatch shutdownLatch = new CountDownLatch((seedNode != null? 1 : 0) + peerNodes.size());

        // Stop the seed node.
        if (seedNode != null) {
            seedNode.shutDown(shutdownLatch::countDown);
        }
        // Stop peer nodes.
        for (P2PService peer : peerNodes) {
            peer.shutDown(shutdownLatch::countDown);
        }
        // Wait for concurrent tasks to finish.
        shutdownLatch.await();

        // Cleanup test data directory.
        if (testDataDir != null) {
            deleteTestDataDirectory();
        }
    }

    @Test
    public void test() throws InterruptedException {
        // Wait for peers to get their preliminary data.
        org.junit.Assert.assertTrue("timed out while waiting for preliminary data",
                prelimDataLatch.await(30, TimeUnit.SECONDS));
        // Wait for peers to complete their bootstrapping.
        org.junit.Assert.assertTrue("timed out while waiting for bootstrap",
                bootstrapLatch.await(30, TimeUnit.SECONDS));

        // Test each peer sending a direct message to another random peer.
        final int nPeers = peerNodes.size();
        BooleanProperty sentDirectFailed = new SimpleBooleanProperty(false);
        final CountDownLatch sentDirectLatch = new CountDownLatch(directCount * nPeers);
        final CountDownLatch receivedDirectLatch = new CountDownLatch(directCount * nPeers);
        final long sendStartMillis = System.currentTimeMillis();
        for (final P2PService srcPeer : peerNodes) {
            // Make the peer ready for receiving direct messages.
            srcPeer.addDecryptedDirectMessageListener((decryptedMsgWithPubKey, peerNodeAddress) -> {
                if (!(decryptedMsgWithPubKey.message instanceof StressTestDirectMessage))
                    return;
                StressTestDirectMessage directMessage = (StressTestDirectMessage) (decryptedMsgWithPubKey.message);
                if ((directMessage.getData().equals("test/" + srcPeer.getAddress())))
                    receivedDirectLatch.countDown();
            });

            long nextSendMillis = System.currentTimeMillis();
            for (int i = 0; i < directCount; i++) {
                // Select a random peer and send a direct message to it...
                final int dstPeerIdx = (int) (Math.random() * nPeers);
                final P2PService dstPeer = peerNodes.get(dstPeerIdx);
                final NodeAddress dstPeerAddress = dstPeer.getAddress();
                // ...after a random delay not shorter than throttle limits.
                nextSendMillis += Math.round(Math.random() * (MAX_DIRECT_DELAY_MILLIS - MIN_DIRECT_DELAY_MILLIS));
                final long sendAfterMillis = nextSendMillis - System.currentTimeMillis();
                /*print("sending direct message from peer %s to %s in %sms",
                        srcPeer.getAddress(), dstPeer.getAddress(), sendAfterMillis);*/
                UserThread.runAfter(() -> srcPeer.sendEncryptedDirectMessage(
                        dstPeerAddress, peerPKRings.get(dstPeerIdx),
                        new StressTestDirectMessage("test/" + dstPeerAddress), new SendDirectMessageListener() {
                            @Override
                            public void onArrived() {
                                sentDirectLatch.countDown();
                            }

                            @Override
                            public void onFault() {
                                sentDirectFailed.set(true);
                                sentDirectLatch.countDown();
                            }
                        }), sendAfterMillis, TimeUnit.MILLISECONDS
                );
            }
        }
        // Since receiving is completed before sending is reported to be complete,
        // all receiving checks should end before all sending checks to avoid deadlocking.
        /** Time to transmit all messages in the worst random case, and with no computation delays. */
        final long idealMaxDirectDelay = MAX_DIRECT_DELAY_MILLIS * directCount;
        // Wait for peers to complete receiving.  We are generous here.
        org.junit.Assert.assertTrue("timed out while receiving direct messages",
                receivedDirectLatch.await(2 * idealMaxDirectDelay, TimeUnit.MILLISECONDS));
        print("receiving %d direct messages per peer took %ss", directCount,
                (System.currentTimeMillis() - sendStartMillis)/1000.0);
        // Wait for peers to complete sending.
        // This should be nearly instantaneous after waiting for reception is completed.
        org.junit.Assert.assertTrue("timed out while sending direct messages",
                sentDirectLatch.await(idealMaxDirectDelay / 10, TimeUnit.MILLISECONDS));
        print("sending %d direct messages per peer took %ss", directCount,
                (System.currentTimeMillis() - sendStartMillis)/1000.0);
        org.junit.Assert.assertFalse("some peer(s) failed to send a direct message", sentDirectFailed.get());
    }

    private void print(String message, Object... args) {
        System.out.println(this.getClass().getSimpleName() + ": "
            + String.format(message, args));
    }

    private Path createTestDataDirectory() throws IOException {
        Path stressTestDirPath;

        final String stressTestDir = System.getenv(TEST_DIR_ENVVAR);
        if ((stressTestDir != null) && !stressTestDir.equals("")) {
            // Test directory specified, use and create if missing.
            stressTestDirPath = Paths.get(stressTestDir);
            if (!Files.isDirectory(stressTestDirPath)) {
                //noinspection ResultOfMethodCallIgnored
                stressTestDirPath.toFile().mkdirs();
            }
        } else {
            stressTestDirPath = Files.createTempDirectory("bsq" + this.getClass().getSimpleName());
        }
        return stressTestDirPath;
    }

    /**
     * Delete the test data directory recursively, unless <code>STRESS_TEST_DIR</code> is defined,
     * in which case peer node keys are kept.
     *
     * @throws IOException
     */
    private void deleteTestDataDirectory() throws IOException {
        // Based on <https://stackoverflow.com/a/27917071/6239236> by Tomasz Dzięcielewski.
        final String stressTestDir = System.getenv(TEST_DIR_ENVVAR);
        final boolean keep = (stressTestDir != null) && !stressTestDir.equals("");
        Files.walkFileTree(testDataDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final String fileName = file.getFileName().toString();
                if (!(keep && (fileName.matches("enc\\.key|sig\\.key|private_key"))))  // peer and tor keys
                    Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                // ``dir`` is always a directory, I/O errors may still trigger ``NullPointerException``.
                //noinspection ConstantConditions
                if (!(keep && dir.toFile().listFiles().length > 0))
                    Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // P2P service listener classes

    private class TestSetupListener implements SetupListener {
        private final CountDownLatch localServicesLatch;
        private final BooleanProperty localServicesFailed;

        TestSetupListener(CountDownLatch localServicesLatch, BooleanProperty localServicesFailed) {
            this.localServicesLatch = localServicesLatch;
            this.localServicesFailed = localServicesFailed;
        }

        @Override
        public void onTorNodeReady() {
            // do nothing
        }

        @Override
        public void onHiddenServicePublished() {
            // successful result
            localServicesLatch.countDown();
        }

        @Override
        public void onSetupFailed(Throwable throwable) {
            // failed result
            localServicesFailed.set(true);
            localServicesLatch.countDown();
        }
    }

    private class SeedServiceListener extends TestSetupListener implements P2PServiceListener {
        SeedServiceListener(CountDownLatch localServicesLatch, BooleanProperty localServicesFailed) {
            super(localServicesLatch, localServicesFailed);
        }

        @Override
        public void onRequestingDataCompleted() {
            // preliminary data not used in single seed node
        }

        @Override
        public void onNoSeedNodeAvailable() {
            // expected in single seed node
        }

        @Override
        public void onNoPeersAvailable() {
            // expected in single seed node
        }

        @Override
        public void onBootstrapComplete() {
            // not used in single seed node
        }
    }

    private class PeerServiceListener extends TestSetupListener implements P2PServiceListener {
        PeerServiceListener(CountDownLatch localServicesLatch, BooleanProperty localServicesFailed) {
            super(localServicesLatch, localServicesFailed);
        }

        @Override
        public void onRequestingDataCompleted() {
            // preliminary data received
            NetworkStressTest.this.prelimDataLatch.countDown();
        }

        @Override
        public void onNoSeedNodeAvailable() {
            // do nothing
        }

        @Override
        public void onNoPeersAvailable() {
            // do nothing
        }

        @Override
        public void onBootstrapComplete() {
            // peer bootstrapped
            NetworkStressTest.this.bootstrapLatch.countDown();
        }
    }
}

// Message classes

final class StressTestDirectMessage implements DirectMessage {
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private final int messageVersion = Version.getP2PMessageVersion();

    private String data;

    StressTestDirectMessage(String data) {
        this.data = data;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    String getData() {
        return data;
    }
}

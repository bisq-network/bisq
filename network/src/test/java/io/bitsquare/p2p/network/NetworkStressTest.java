package io.bitsquare.p2p.network;

import io.bitsquare.common.Clock;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.KeyStorage;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.Utils;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NetworkStressTest {
    // Test parameters

    /** Number of peer nodes to create. */
    private static final int NPEERS = 4;

    // Constants

    /** Numeric identifier of the regtest Bitcoin network. */
    private static final int REGTEST_NETWORK_ID = 2;
    /** Environment variable to specify a persistent test data directory. */
    private static final String TEST_DIR_ENVVAR = "STRESS_TEST_DIR";

    // Instance fields

    /** A directory to temporarily hold seed and normal nodes' configuration and state files. */
    private Path tempDir;
    /** A single seed node that other nodes will contact to request initial data. */
    private SeedNode seedNode;
    /** A list of peer nodes represented as P2P services. */
    private List<P2PService> peerNodes = new ArrayList<>();

    /** A barrier to wait for concurrent reception of preliminary data in peers. */
    private CountDownLatch prelimDataLatch = new CountDownLatch(NPEERS);
    /** A barrier to wait for concurrent bootstrap of peers. */
    private CountDownLatch bootstrapLatch = new CountDownLatch(NPEERS);

    @Before
    public void setUp() throws Exception {
        /** A property where threads can indicate setup failure of local services (Tor node, hidden service). */
        BooleanProperty localServicesFailed = new SimpleBooleanProperty(false);
        /** A barrier to wait for concurrent setup of local services (Tor node, hidden service). */
        final CountDownLatch localServicesLatch = new CountDownLatch(1 /*seed node*/ + NPEERS);

        // Set a security provider to allow key generation.
        Security.addProvider(new BouncyCastleProvider());

        // Create the temporary directory.
        tempDir = createTempDirectory();

        // Create and start the seed node.
        seedNode = new SeedNode(tempDir.toString());
        final NodeAddress seedNodeAddress = getSeedNodeAddress();
        final boolean useLocalhost = seedNodeAddress.hostName.equals("localhost");
        final Set<NodeAddress> seedNodes = new HashSet<>(1);
        seedNodes.add(seedNodeAddress);  // the only seed node in tests
        seedNode.createAndStartP2PService(seedNodeAddress, useLocalhost,
                REGTEST_NETWORK_ID, true /*detailed logging*/, seedNodes,
                new SeedServiceListener(localServicesLatch, localServicesFailed));

        // Create and start peer nodes.
        SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (useLocalhost) {
            seedNodesRepository.setLocalhostSeedNodeAddresses(seedNodes);
        } else {
            seedNodesRepository.setTorSeedNodeAddresses(seedNodes);
        }
        for (int p = 0; p < NPEERS; p++) {
            final int peerPort = Utils.findFreeSystemPort();
            final File peerDir = new File(tempDir.toFile(), String.format("peer-%06d", p));
            final File peerTorDir = new File(peerDir, "tor");
            final File peerStorageDir = new File(peerDir, "db");
            final File peerKeysDir = new File(peerDir, "keys");
            //noinspection ResultOfMethodCallIgnored
            peerKeysDir.mkdirs();  // needed for creating the key ring
            final KeyStorage peerKeyStorage = new KeyStorage(peerKeysDir);
            final KeyRing peerKeyRing = new KeyRing(peerKeyStorage);
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

        // Cleanup temporary directory.
        if (tempDir != null) {
            deleteTempDirectory();
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
    }

    private Path createTempDirectory() throws IOException {
        Path stressTestDirPath;

        String stressTestDir = System.getenv(TEST_DIR_ENVVAR);
        if (stressTestDir != null) {
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

    private void deleteTempDirectory() throws IOException {
        // Based on <https://stackoverflow.com/a/27917071/6239236> by Tomasz Dzięcielewski.
        if (System.getenv(TEST_DIR_ENVVAR) != null)
            return;  // do not remove if given explicitly
        Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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

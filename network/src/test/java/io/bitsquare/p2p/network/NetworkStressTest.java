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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class NetworkStressTest {
    /** Numeric identifier of the regtest Bitcoin network. */
    private static final int REGTEST_NETWORK_ID = 2;
    /** Number of peer nodes to create. */
    private static final int NPEERS = 1;

    /** A directory to temporarily hold seed and normal nodes' configuration and state files. */
    private Path tempDir;
    /** A single seed node that other nodes will contact to request initial data. */
    private SeedNode seedNode;
    /** A list of peer nodes represented as P2P services. */
    private List<P2PService> peerNodes = new ArrayList<>();

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
                getSetupListener(localServicesFailed, localServicesLatch));

        // Create and start peer nodes.
        SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (useLocalhost) {
            seedNodesRepository.setLocalhostSeedNodeAddresses(seedNodes);
        } else {
            seedNodesRepository.setTorSeedNodeAddresses(seedNodes);
        }
        for (int p = 0; p < NPEERS; p++) {
            final int peerPort = Utils.findFreeSystemPort();
            final File peerDir = new File(tempDir.toFile(), "Bitsquare_peer_" + peerPort);
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
            peer.start(getSetupListener(localServicesFailed, localServicesLatch));
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
        return new NodeAddress("localhost", Utils.findFreeSystemPort());
    }

    @NotNull
    private P2PServiceListener getSetupListener(
            final BooleanProperty localServicesFailed,
            final CountDownLatch localServicesLatch) {
        return new P2PServiceListener() {
            @Override
            public void onRequestingDataCompleted() {
                // do nothing
            }

            @Override
            public void onNoSeedNodeAvailable() {
                // expected, do nothing
            }

            @Override
            public void onNoPeersAvailable() {
                // expected, do nothing
            }

            @Override
            public void onBootstrapComplete() {
                // do nothing
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
        };
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

        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void test() throws InterruptedException {
        // do nothing
        Thread.sleep(30_000);
    }

    private Path createTempDirectory() throws IOException {
        return Files.createTempDirectory("bsq" + this.getClass().getSimpleName());
    }

    private static void deleteRecursively(@NotNull final Path path) throws IOException {
        // Based on <https://stackoverflow.com/a/27917071/6239236> by Tomasz Dzięcielewski.
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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
}

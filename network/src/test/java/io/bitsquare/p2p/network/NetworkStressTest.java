package io.bitsquare.p2p.network;

import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.seed.SeedNode;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class NetworkStressTest {
    /** A directory to temporarily hold seed and normal nodes' configuration and state files. */
    private Path tempDir;
    /** A single seed node that other nodes will contact to request initial data. */
    private SeedNode seedNode;

    @Before
    public void setUp() throws IOException, InterruptedException {
        /** A barrier to wait for concurrent tasks. */
        final CountDownLatch pendingTasks = new CountDownLatch(1 /*seed node*/);

        // Create the temporary directory.
        tempDir = createTempDirectory();

        // Create and start the seed node.
        seedNode = new SeedNode(tempDir.toString());
        final NodeAddress seedNodeAddress = new NodeAddress("localhost:8002");
        final boolean useLocalhost = true;
        final Set<NodeAddress> seedNodes = new HashSet<>(1);
        seedNodes.add(seedNodeAddress);  // the only seed node in tests
        boolean seedNodeSetupFailed = false;
        seedNode.createAndStartP2PService(seedNodeAddress, useLocalhost,
                2 /*regtest*/, true /*detailed logging*/, seedNodes,
                new P2PServiceListener() {
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
                        pendingTasks.countDown();  // one less task to wait on
                    }

                    @Override
                    public void onSetupFailed(Throwable throwable) {
                        //XXXX
                    }
                }
        );

        // Wait for concurrent tasks to finish.
        pendingTasks.await();
    }

    @After
    public void tearDown() throws InterruptedException, IOException {
        /** A barrier to wait for concurrent tasks. */
        final CountDownLatch pendingTasks = new CountDownLatch(seedNode != null? 1 : 0);

        // Stop the seed node.
        if (seedNode != null) {
            seedNode.shutDown(pendingTasks::countDown);
        }
        // Wait for concurrent tasks to finish.
        pendingTasks.await();

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
                Files.delete(path);
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

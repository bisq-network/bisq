package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.bitsquare.common.UserThread;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class NetworkStressTest {
    private Path tempDir;
    private SeedNode seedNode;

    @Before
    public void setup() throws IOException, InterruptedException {
        // Use an executor that uses a single daemon thread.
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(this.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        tempDir = createTempDirectory();
        seedNode = new SeedNode(tempDir.toString());

        // Use as a barrier to wait for concurrent tasks.
        final CountDownLatch latch = new CountDownLatch(1 /*seed node*/);
        // Start the seed node.
        final NodeAddress seedNodeAddress = new NodeAddress("localhost:8002");
        UserThread.execute(() -> {
            try {
                seedNode.createAndStartP2PService(seedNodeAddress, true /*localhost*/,
                        2 /*regtest*/, false /*detailed logging*/, null /*seed nodes*/,
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
                                latch.countDown();  // one less task to wait on
                            }

                            @Override
                            public void onTorNodeReady() {
                                // do nothing
                            }

                            @Override
                            public void onHiddenServicePublished() {
                                // do nothing
                            }

                            @Override
                            public void onSetupFailed(Throwable throwable) {
                                //XXXX
                            }
                        });
            } catch (Throwable t) {
                //log.error("Executing task failed. " + t.getMessage());
                t.printStackTrace();
            }
        });

        // Wait for concurrent tasks to finish.
        latch.await();
    }

    @After
    public void tearDown() throws InterruptedException, IOException {
        // Use as a barrier to wait for concurrent tasks.
        final CountDownLatch latch = new CountDownLatch(1 /*seed node*/);
        // Stop the seed node.
        if (seedNode != null) {
            seedNode.shutDown(() -> {latch.countDown();});
        }
        // Wait for concurrent tasks to finish.
        latch.await();

        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void test() {
        // do nothing
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

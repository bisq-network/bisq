package io.bisq.network.p2p.network;

import io.bisq.common.UserThread;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.util.Tuple3;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.*;
import io.bisq.network.p2p.messaging.DecryptedMailboxListener;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.Security;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * bisq network stress tests.
 * <p/>
 * You can invoke this class directly from the command line.
 * If the name of a single test is given as an argument, only that test is run.
 * Otherwise all tests in the class are run.
 * <p/>
 * You can also set some {@code STRESS_TEST_*} environment variables to
 * customize the execution of tests.
 * See the {@code *_ENVVAR} constants for the names of these variables.
 */

// TODO deactivated because outdated
@Ignore
public class NetworkStressTest {
    // Test parameters

    /**
     * Whether to log network_messages less important than warnings.
     */
    private static final boolean USE_DETAILED_LOGGING = false;

    // Constants

    /**
     * Environment variable to specify the number of peers in the test.
     */
    private static final String NPEERS_ENVVAR = "STRESS_TEST_NPEERS";
    /**
     * Environment variable to specify a persistent test data directory.
     */
    private static final String TEST_DIR_ENVVAR = "STRESS_TEST_DIR";
    /**
     * Environment variable to specify the number of direct network_messages sent per peer.
     */
    private static final String DIRECT_COUNT_ENVVAR = "STRESS_TEST_NDIRECT";
    /**
     * Environment variable to specify the number of mailbox network_messages sent per peer.
     */
    private static final String MAILBOX_COUNT_ENVVAR = "STRESS_TEST_NMAILBOX";

    /**
     * Numeric identifier of the regtest Bitcoin network.
     */
    private static final int REGTEST_NETWORK_ID = 2;

    /**
     * Default number of peers in the test.
     */
    private static final int NPEERS_DEFAULT = 4;
    /**
     * Minimum number of peers for the test to work (2 for direct network_messages, 3 for mailbox network_messages).
     */
    private static final int NPEERS_MIN = 3;
    /**
     * Default number of direct network_messages to be sent by each peer.
     */
    private static final int DIRECT_COUNT_DEFAULT = 100;
    /**
     * Default number of mailbox network_messages to be sent by each peer.
     */
    private static final int MAILBOX_COUNT_DEFAULT = 100;

    /**
     * Maximum delay in seconds for a node to receive preliminary data.
     */
    private static final long MAX_PRELIMINARY_DELAY_SECS = 5;
    /**
     * Maximum delay in seconds for a node to bootstrap after receiving preliminary data.
     */
    private static final long MAX_BOOTSTRAP_DELAY_SECS = 5;
    /**
     * Maximum delay in seconds for a node to shutdown.
     */
    private static final long MAX_SHUTDOWN_DELAY_SECS = 2;
    /**
     * Minimum delay between direct network_messages in milliseconds, 25% larger than throttle limit.
     */
    private static final long MIN_DIRECT_DELAY_MILLIS = Math.round(1.25 * (1.0 / Connection.MSG_THROTTLE_PER_SEC) * 1000);
    /**
     * Maximum delay between direct network_messages in milliseconds, 10 times larger than minimum.
     */
    private static final long MAX_DIRECT_DELAY_MILLIS = 10 * MIN_DIRECT_DELAY_MILLIS;
    /**
     * Estimated delay in seconds to send or receive a mailbox message.
     */
    private static final long MAILBOX_DELAY_SECS = 2;

    // Instance fields

    /**
     * The last time a progress bar update was printed (to throttle message printing).
     */
    private long lastProgressUpdateMillis = 0;
    /**
     * A directory to (temporarily) hold seed and normal nodes' configuration and state files.
     */
    private Path testDataDir;
    /**
     * Whether to use localhost addresses instead of Tor hidden services.
     */
    private boolean useLocalhostForP2P;
    /**
     * A single seed node that other nodes will contact to request initial data.
     */
    private DummySeedNode seedNode;
    /**
     * The repository of seed nodes used in the test.
     */
    private final SeedNodesRepository seedNodesRepository = null;
    /**
     * A list of peer nodes represented as P2P services.
     */
    private final List<P2PService> peerNodes = new ArrayList<>();
    /**
     * A list of peer node's service ports.
     */
    private final List<Integer> peerPorts = new ArrayList<>();
    /**
     * A list of peer node's public key rings.
     */
    private final List<PubKeyRing> peerPKRings = new ArrayList<>();

    /**
     * Number of direct network_messages to be sent by each peer.
     */
    private int directCount = DIRECT_COUNT_DEFAULT;
    /**
     * Number of mailbox network_messages to be sent by each peer.
     */
    private int mailboxCount = MAILBOX_COUNT_DEFAULT;


    // # MAIN ENTRY POINT

    // Inspired by <https://stackoverflow.com/a/9288513> by Marc Peters.
    public static void main(String[] args) {
        Request request = (args.length == 0)
                ? Request.aClass(NetworkStressTest.class)
                : Request.method(NetworkStressTest.class, args[0]);

        Result result = new JUnitCore().run(request);
        for (Failure f : result.getFailures())
            System.err.printf("\n%s\n%s", f, f.getTrace());
        System.exit(result.wasSuccessful() ? 0 : 1);
    }


    // # COMMON UTILITIES

    private void print(String message, Object... args) {
        System.out.println(this.getClass().getSimpleName() + ": "
                + String.format(message, args));
    }

    /**
     * Decrease latch count and print a progress indicator based on the given character.
     */
    private void countDownAndPrint(CountDownLatch latch, char c) {
        latch.countDown();
        printProgress(c, (int) latch.getCount());
    }

    /**
     * Print a progress indicator based on the given character.
     */
    private void printProgress(char c, int n) {
        if (n < 1)
            return;  // completed tasks are not shown

        // Do not print the indicator if the last one was shown less than half second ago.
        long now = System.currentTimeMillis();
        if ((now - lastProgressUpdateMillis) < 500)
            return;
        lastProgressUpdateMillis = now;

        // Keep a fixed length so that indicators do not overwrite partially.
        System.out.print(String.format("\r%s> %c*%-6d ", this.getClass().getSimpleName(), c, n));
        System.out.flush();
    }

    private static void assertLatch(String message, CountDownLatch latch, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (!latch.await(timeout, unit))
            org.junit.Assert.fail(String.format("%s (%d pending in latch)", message, latch.getCount()));
    }

    private Tuple3<Long, Long, Long> minMaxAvg(List<Long> l) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long sum = 0;
        for (long e : l) {
            if (e < min)
                min = e;
            if (e > max)
                max = e;
            sum += e;
        }
        return new Tuple3<>(min, max, sum / l.size());
    }


    // # TEST SETUP

    @Before
    public void setUp() throws Exception {
        // Parse test parameter environment variables.

        /** Number of peer nodes to create. */
        final int nPeers = parseEnvInt(NPEERS_ENVVAR, NPEERS_DEFAULT, NPEERS_MIN);
        directCount = parseEnvInt(DIRECT_COUNT_ENVVAR, DIRECT_COUNT_DEFAULT, 0);
        mailboxCount = parseEnvInt(MAILBOX_COUNT_ENVVAR, MAILBOX_COUNT_DEFAULT, 0);

        /** A property where threads can indicate setup failure of local services (Tor node, hidden service). */
        final BooleanProperty localServicesFailed = new SimpleBooleanProperty(false);
        /** A barrier to wait for concurrent setup of local services (Tor node, hidden service). */
        final CountDownLatch localServicesLatch = new CountDownLatch(1 /*seed node*/ + nPeers);
        /* A barrier to wait for concurrent reception of preliminary data in peers. */
        final CountDownLatch prelimDataLatch = new CountDownLatch(nPeers);
        /* A barrier to wait for concurrent bootstrap of peers. */
        final CountDownLatch bootstrapLatch = new CountDownLatch(nPeers);

        // Set a security provider to allow key generation.
        Security.addProvider(new BouncyCastleProvider());

        // Create the test data directory.
        testDataDir = createTestDataDirectory();
        print("test data directory: " + testDataDir);

        // Setting the executor seems to make tests more stable against ``ConcurrentModificationException``
        // (see #443).  However it make it use more open files, so you may need to use ``ulimit -n NUMBER``
        // or run ``prlimit -nNUMBER -pPID`` (as root) on your shell's PID if you get too many open files errors.
        // NUMBER=16384 seems to be enough for 100 peers in Debian GNU/Linux.
        UserThread.setExecutor(Executors.newSingleThreadExecutor());

        // Create and start the seed node.
        seedNode = new DummySeedNode(testDataDir.toString());
        final NodeAddress seedNodeAddress = newSeedNodeAddress();
        useLocalhostForP2P = seedNodeAddress.getHostName().equals("localhost");
        final Set<NodeAddress> seedNodes = new HashSet<>(1);
        seedNodes.add(seedNodeAddress);  // the only seed node in tests
        seedNode.createAndStartP2PService(seedNodeAddress, DummySeedNode.MAX_CONNECTIONS_DEFAULT, useLocalhostForP2P,
                REGTEST_NETWORK_ID, USE_DETAILED_LOGGING, seedNodes,
                new SeedServiceListener(localServicesLatch, localServicesFailed));
        print("created seed node");

        // Create and start peer nodes, all connecting to the seed node above.
        if (useLocalhostForP2P) {
            seedNodesRepository.setLocalhostSeedNodeAddresses(seedNodes);
        } else {
            seedNodesRepository.setTorSeedNodeAddresses(seedNodes);
        }
        for (int p = 0; p < nPeers; p++) {
            // peer network port
            final int peerPort = Utils.findFreeSystemPort();
            peerPorts.add(peerPort);
            // create, save and start peer
            final P2PService peer = createPeerNode(p, peerPort);
            //noinspection ConstantConditions
            peerPKRings.add(peer.getKeyRing().getPubKeyRing());
            peerNodes.add(peer);
            peer.start(new PeerServiceListener(
                    localServicesLatch, localServicesFailed, prelimDataLatch, bootstrapLatch));
        }
        print("created peer nodes");

        // Wait for concurrent tasks to finish.
        localServicesLatch.await();

        // Check if any node reported setup failure on start.
        if (localServicesFailed.get()) {
            throw new Exception("nodes failed to start");
        }

        print("all local nodes started");

        // Wait for peers to get their preliminary data.
        assertLatch("timed out while waiting for preliminary data",
                prelimDataLatch, MAX_PRELIMINARY_DELAY_SECS * nPeers, TimeUnit.SECONDS);
        print("preliminary data received");

        // Wait for peers to complete their bootstrapping.
        assertLatch("timed out while waiting for bootstrap",
                bootstrapLatch, MAX_BOOTSTRAP_DELAY_SECS * nPeers, TimeUnit.SECONDS);
        print("bootstrap complete");
    }

    /**
     * Parse an integer value from the given environment variable, with default and minimum values.
     */
    private int parseEnvInt(String envVar, int defValue, int minValue) {
        int value = defValue;
        final String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.equals(""))
            value = Integer.parseInt(envValue);
        if (value < minValue)
            throw new IllegalArgumentException(
                    String.format("%s must be at least %d: %d", envVar, minValue, value)
            );
        return value;
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

    @NotNull
    private static NodeAddress newSeedNodeAddress() {
        // The address is only considered by ``SeedNodesRepository`` if
        // it ends in the digit matching the network identifier.
        int port;
        do {
            port = Utils.findFreeSystemPort();
        } while (port % 10 != REGTEST_NETWORK_ID);
        return new NodeAddress("localhost", port);
    }

    @NotNull
    private P2PService createPeerNode(int n, int port) {
        // peer data directories
        final File peerDir = new File(testDataDir.toFile(), String.format("peer-%06d", n));
        final File peerTorDir = new File(peerDir, "tor");
        final File peerStorageDir = new File(peerDir, "db");
        final File peerKeysDir = new File(peerDir, "keys");
        //noinspection ResultOfMethodCallIgnored
        peerKeysDir.mkdirs();  // needed for creating the key ring

        // peer keys
        final KeyStorage peerKeyStorage = new KeyStorage(peerKeysDir);
        final KeyRing peerKeyRing = new KeyRing(peerKeyStorage);
        final EncryptionService peerEncryptionService = new EncryptionService(peerKeyRing, TestUtils.getNetworkProtoResolver());

        return null;
        /*new P2PService(seedNodesRepository, port, peerTorDir, useLocalhostForP2P,
                REGTEST_NETWORK_ID, P2PService.MAX_CONNECTIONS_DEFAULT, peerStorageDir,0,null,false, 0, null, null, null,
                new Clock(), null, peerEncryptionService, peerKeyRing,
                TestUtils.getNetworkProtoResolver());*/
    }

    // ## TEST SETUP: P2P service listener classes

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

        @Override
        public void onRequestCustomBridges() {

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
        private final CountDownLatch prelimDataLatch;
        private final CountDownLatch bootstrapLatch;

        PeerServiceListener(CountDownLatch localServicesLatch, BooleanProperty localServicesFailed,
                            CountDownLatch prelimDataLatch, CountDownLatch bootstrapLatch) {
            super(localServicesLatch, localServicesFailed);
            this.prelimDataLatch = prelimDataLatch;
            this.bootstrapLatch = bootstrapLatch;
        }

        @Override
        public void onRequestingDataCompleted() {
            // preliminary data received
            countDownAndPrint(prelimDataLatch, 'p');
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
            countDownAndPrint(bootstrapLatch, 'b');
        }
    }


    // # TEST CLEANUP

    @After
    public void tearDown() throws InterruptedException, IOException {
        /** A barrier to wait for concurrent shutdown of services. */

        final int nNodes = (seedNode != null ? 1 : 0) + peerNodes.size();
        final CountDownLatch shutdownLatch = new CountDownLatch(nNodes);

        print("stopping all local nodes");
        // Stop peer nodes.
        for (P2PService peer : peerNodes) {
            peer.shutDown(() -> countDownAndPrint(shutdownLatch, '.'));
        }
        // Stop the seed node.
        if (seedNode != null) {
            seedNode.shutDown(() -> countDownAndPrint(shutdownLatch, '.'));
        }
        // Wait for concurrent tasks to finish.
        assertLatch("timed out while stopping nodes",
                shutdownLatch, MAX_SHUTDOWN_DELAY_SECS * nNodes, TimeUnit.SECONDS);
        print("all local nodes stopped");

        // Cleanup test data directory.
        print("cleaning up test data directory");
        if (testDataDir != null) {
            deleteTestDataDirectory();
        }
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


    // # DIRECT SENDING AND RECEIVING

    /**
     * Test each peer sending a direct message to another random peer.
     */
    @Test
    public void test_direct() throws InterruptedException {
        final int nPeers = peerNodes.size();
        BooleanProperty sentDirectFailed = new SimpleBooleanProperty(false);
        final List<Long> sentDelays = new Vector<>(nPeers * directCount);
        final CountDownLatch sentDirectLatch = new CountDownLatch(directCount * nPeers);
        final CountDownLatch receivedDirectLatch = new CountDownLatch(directCount * nPeers);
        final long sendStartMillis = System.currentTimeMillis();
        for (final P2PService srcPeer : peerNodes) {
            final NodeAddress srcPeerAddress = srcPeer.getAddress();

            // Make the peer ready for receiving direct network_messages.
            srcPeer.addDecryptedDirectMessageListener((decryptedDirectMessageListener, peerNodeAddress) -> {
                if (!(decryptedDirectMessageListener.getNetworkEnvelope() instanceof StressTestDirectMessage))
                    return;
                StressTestDirectMessage directMessage = (StressTestDirectMessage) (decryptedDirectMessageListener.getNetworkEnvelope());
                if ((directMessage.getData().equals("test/" + srcPeerAddress)))
                    receivedDirectLatch.countDown();
            });

            long nextSendMillis = System.currentTimeMillis();
            for (int i = 0; i < directCount; i++) {
                // Select a random peer (different than source one) and send a direct message to it...
                int peerIdx;
                NodeAddress peerAddr;
                do {
                    peerIdx = (int) (Math.random() * nPeers);
                    peerAddr = peerNodes.get(peerIdx).getAddress();
                } while (srcPeerAddress.equals(peerAddr));
                final int dstPeerIdx = peerIdx;
                final NodeAddress dstPeerAddress = peerAddr;
                // ...after a random delay not shorter than throttle limits.
                nextSendMillis += Math.round(Math.random() * (MAX_DIRECT_DELAY_MILLIS - MIN_DIRECT_DELAY_MILLIS));
                final long sendAfterMillis = nextSendMillis - System.currentTimeMillis();
                /*print("sending direct message from peer %s to %s in %sms",
                        srcPeer.getAddress(), dstPeer.getAddress(), sendAfterMillis);*/
                UserThread.runAfter(() -> {
                            final long sendMillis = System.currentTimeMillis();
                            srcPeer.sendEncryptedDirectMessage(
                                    dstPeerAddress, peerPKRings.get(dstPeerIdx),
                                    new StressTestDirectMessage("test/" + dstPeerAddress),
                                    new SendDirectMessageListener() {
                                        @Override
                                        public void onArrived() {
                                            sentDelays.add(System.currentTimeMillis() - sendMillis);
                                            countDownAndPrint(sentDirectLatch, 'd');
                                        }

                                        @Override
                                        public void onFault() {
                                            sentDirectFailed.set(true);
                                            countDownAndPrint(sentDirectLatch, 'd');
                                        }
                                    });
                        }, sendAfterMillis, TimeUnit.MILLISECONDS
                );
            }
        }
        print("%d direct network_messages scheduled to be sent by each of %d peers", directCount, nPeers);
        // Since receiving is completed before sending is reported to be complete,
        // all receiving checks should end before all sending checks to avoid deadlocking.
        /** Time to transmit all network_messages in the worst random case, and with no computation delays. */
        final long idealMaxDirectDelay = MAX_DIRECT_DELAY_MILLIS * directCount;
        // Wait for peers to complete receiving.  We are generous here.
        assertLatch("timed out while receiving direct network_messages",
                receivedDirectLatch, 25 * idealMaxDirectDelay, TimeUnit.MILLISECONDS);
        final long recvMillis = System.currentTimeMillis() - sendStartMillis;
        print("receiving %d direct network_messages per peer took %ss (%.2f x ideal max)",
                directCount, recvMillis / 1000.0, recvMillis / (float) idealMaxDirectDelay);
        // Wait for peers to complete sending.
        // This should be nearly instantaneous after waiting for reception is completed.
        assertLatch("timed out while sending direct network_messages",
                sentDirectLatch, idealMaxDirectDelay / 10, TimeUnit.MILLISECONDS);
        Tuple3<Long, Long, Long> mma = minMaxAvg(sentDelays);
        print("sending %d direct network_messages per peer took %ss (min/max/avg %s/%s/%s ms)",
                directCount, (System.currentTimeMillis() - sendStartMillis) / 1000.0,
                mma.first, mma.second, mma.third);
        org.junit.Assert.assertFalse("some peer(s) failed to send a direct message", sentDirectFailed.get());
    }


    // # DIRECT + MAILBOX SENDING AND RECEIVING

    /**
     * Test sending and receiving mailbox network_messages.
     */
    @Test
    public void test_mailbox() throws InterruptedException {
        // We start by putting the first half of peers online and the second one offline.
        // Then the first online peer sends a number of network_messages to random peers (regardless of their state),
        // so that some network_messages are delivered directly and others into a mailbox.
        // Then the first online peer is put offline and the last offline peer is put online
        // (so it can get its mailbox network_messages),
        // and the new first online node sends network_messages.
        // This is repeated until all nodes have been online and offline.

        final int nPeers = peerNodes.size();
        // No sent latch here since the order of events is different
        // depending on whether the message goes direct or via mailbox.
        final CountDownLatch receivedMailboxLatch = new CountDownLatch(mailboxCount * nPeers);

        // Configure the first half of peers to receive network_messages...
        int firstPeerDown = (int) Math.ceil(nPeers / 2.0);
        for (P2PService peer : peerNodes.subList(0, firstPeerDown)) {
            addMailboxListeners(peer, receivedMailboxLatch);
        }
        // ...and put the second half offline.
        final CountDownLatch halfShutDown = new CountDownLatch(nPeers / 2);
        for (P2PService peer : peerNodes.subList(firstPeerDown, nPeers)) {
            peer.shutDown(halfShutDown::countDown);
        }
        assertLatch("timed out while stopping a half of the peers",
                halfShutDown, MAX_SHUTDOWN_DELAY_SECS * nPeers, TimeUnit.SECONDS);
        //print("stopped a half of the peers for mailbox test");

        // Cycle through peers sending to others, stopping the peer
        // and starting one of the stopped peers.
        print("%d mailbox network_messages to be sent by each of %d peers", mailboxCount, nPeers);
        BooleanProperty sentMailboxFailed = new SimpleBooleanProperty(false);
        final long sendStartMillis = System.currentTimeMillis();
        for (int firstOnline = 0, firstOffline = firstPeerDown;
             firstOnline < nPeers;
             firstOnline++, firstOffline = ++firstOffline % nPeers) {
            // The first online peer sends network_messages to random other peers.
            final P2PService onlinePeer = peerNodes.get(firstOnline);
            final NodeAddress onlinePeerAddress = onlinePeer.getAddress();
            final CountDownLatch sendLatch = new CountDownLatch(mailboxCount);
            for (int i = 0; i < mailboxCount; i++) {
                // Select a random peer (different than source one)...
                int peerIdx;
                NodeAddress peerAddr;
                do {
                    peerIdx = (int) (Math.random() * nPeers);
                    peerAddr = peerNodes.get(peerIdx).getAddress();
                } while (onlinePeerAddress.equals(peerAddr));
                final int dstPeerIdx = peerIdx;
                final NodeAddress dstPeerAddress = peerAddr;
                // ...and send a message to it.
                onlinePeer.sendEncryptedMailboxMessage(dstPeerAddress, peerPKRings.get(dstPeerIdx),
                        new StressTestMailboxMessage(onlinePeerAddress, "test/" + dstPeerAddress),
                        new SendMailboxMessageListener() {  // checked in receiver
                            @Override
                            public void onArrived() {
                                sendLatch.countDown();
                            }

                            @Override
                            public void onStoredInMailbox() {
                                sendLatch.countDown();
                            }

                            @Override
                            public void onFault(String errorMessage) {
                                sentMailboxFailed.set(true);
                                sendLatch.countDown();
                            }
                        });
            }
            assertLatch("timed out while sending from peer " + firstOnline,
                    sendLatch, MAILBOX_DELAY_SECS * mailboxCount, TimeUnit.SECONDS);

            // When done, put first online peer offline.
            final CountDownLatch stopLatch = new CountDownLatch(1);
            onlinePeer.shutDown(stopLatch::countDown);
            assertLatch("timed out while stopping peer " + firstOnline,
                    stopLatch, MAX_SHUTDOWN_DELAY_SECS, TimeUnit.SECONDS);
            //print("put peer %d offline", firstOnline);

            // When done, put first offline peer online and setup message listeners.
            final CountDownLatch startLatch = new CountDownLatch(1);
            final P2PService startedPeer = createPeerNode(firstOffline, peerPorts.get(firstOffline));
            addMailboxListeners(startedPeer, receivedMailboxLatch);
            peerNodes.set(firstOffline, startedPeer);
            startedPeer.start(new MailboxStartListener(startLatch));
            assertLatch("timed out while starting peer " + firstOffline,
                    startLatch,
                    // this assumes some delay per received mailbox message
                    (MAX_PRELIMINARY_DELAY_SECS + MAX_BOOTSTRAP_DELAY_SECS) + MAILBOX_DELAY_SECS * nPeers,
                    TimeUnit.SECONDS);
            //print("put peer %d online", firstOffline);
        }
        /** Time to transmit all network_messages with the estimated per-message delay, with no computation delays. */
        final long idealMaxMailboxDelay = 2 * MAILBOX_DELAY_SECS * 1000 * nPeers * mailboxCount;
        assertLatch("timed out while receiving mailbox network_messages",
                receivedMailboxLatch, idealMaxMailboxDelay, TimeUnit.MILLISECONDS);
        final long recvMillis = System.currentTimeMillis() - sendStartMillis;
        print("receiving %d mailbox network_messages per peer took %ss (%.2f x ideal max)",
                mailboxCount, recvMillis / 1000.0, recvMillis / (float) idealMaxMailboxDelay);
        org.junit.Assert.assertFalse("some peer(s) failed to send a message", sentMailboxFailed.get());
    }

    /**
     * Configure the peer to decrease the latch on receipt of mailbox message (direct or via mailbox).
     */
    private void addMailboxListeners(P2PService peer, CountDownLatch receivedMailboxLatch) {
        class MailboxMessageListener implements DecryptedDirectMessageListener, DecryptedMailboxListener {
            private void handle(DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
                if (!(decryptedMessageWithPubKey.getNetworkEnvelope() instanceof StressTestMailboxMessage))
                    return;
                StressTestMailboxMessage msg = (StressTestMailboxMessage) (decryptedMessageWithPubKey.getNetworkEnvelope());
                if ((msg.getData().equals("test/" + peer.getAddress())))
                    countDownAndPrint(receivedMailboxLatch, 'm');
            }

            @Override
            public void onDirectMessage(
                    DecryptedMessageWithPubKey decryptedMessageWithPubKey, NodeAddress srcNodeAddress) {
                handle(decryptedMessageWithPubKey);
            }

            @Override
            public void onMailboxMessageAdded(
                    DecryptedMessageWithPubKey decryptedMessageWithPubKey, NodeAddress srcNodeAddress) {
                handle(decryptedMessageWithPubKey);
            }
        }

        final MailboxMessageListener listener = new MailboxMessageListener();
        peer.addDecryptedDirectMessageListener(listener);
        peer.addDecryptedMailboxListener(listener);
    }

    private class MailboxStartListener implements P2PServiceListener {
        private final CountDownLatch startLatch;

        MailboxStartListener(CountDownLatch startLatch) {
            this.startLatch = startLatch;
        }

        @Override
        public void onRequestingDataCompleted() {
        }

        @Override
        public void onNoSeedNodeAvailable() {
        }

        @Override
        public void onNoPeersAvailable() {
        }

        @Override
        public void onBootstrapComplete() {
            startLatch.countDown();
        }

        @Override
        public void onTorNodeReady() {
        }

        @Override
        public void onHiddenServicePublished() {
        }

        @Override
        public void onSetupFailed(Throwable throwable) {
        }

        @Override
        public void onRequestCustomBridges() {

        }
    }
}


// # MESSAGE CLASSES

final class StressTestDirectMessage extends NetworkEnvelope implements DirectMessage {
    private final int messageVersion = Version.getP2PMessageVersion();

    private final String data;

    StressTestDirectMessage(String data) {
        super(0);
        this.data = data;
    }

    String getData() {
        return data;
    }
}

@EqualsAndHashCode(callSuper = true)
@Value
final class StressTestMailboxMessage extends NetworkEnvelope implements MailboxMessage {
    private final int messageVersion = Version.getP2PMessageVersion();
    private final String uid = UUID.randomUUID().toString();
    private final NodeAddress senderNodeAddress;
    private final String data;

    StressTestMailboxMessage(NodeAddress sender, String data) {
        super(0);
        this.senderNodeAddress = sender;
        this.data = data;
    }
}

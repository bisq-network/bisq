/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.msg;

import io.bitsquare.network.BootstrapNode;
import io.bitsquare.network.Node;

import java.io.IOException;

import java.net.UnknownHostException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.tomp2p.connection.Ports;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.futures.FuturePeerConnection;
import net.tomp2p.nat.FutureNAT;
import net.tomp2p.nat.FutureRelayNAT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * Test bootstrapping, DHT operations like put/get/add/remove and sendDirect in both LAN and WAN environment
 * Test scenarios in direct connection, auto port forwarding or relay mode.
 * <p>
 * The start a seed node code use the {@link SeedNodeForTesting} class.
 * <p>
 * To configure your test environment edit the static fields for id, IP and port.
 * In the configure method and the connectionType you can define your test scenario.
 */
@Ignore
public class TomP2PTests {
    private static final Logger log = LoggerFactory.getLogger(TomP2PTests.class);

    private enum ConnectionType {
        UNKNOWN, DIRECT, NAT, RELAY
    }

    // If you want to test in one specific connection mode define it directly, otherwise use UNKNOWN
    private static final ConnectionType FORCED_CONNECTION_TYPE = ConnectionType.DIRECT;

    // Typically you run the seed node in localhost to test direct connection.
    // If you have a setup where you are not behind a router you can also use a WAN side seed node.
    private static final Node BOOTSTRAP_NODE =
            (FORCED_CONNECTION_TYPE == ConnectionType.DIRECT) ? BootstrapNode.LOCALHOST : BootstrapNode.DIGITAL_OCEAN1;

    private static final PeerAddress BOOTSTRAP_NODE_ADDRESS;

    static {
        try {
            BOOTSTRAP_NODE_ADDRESS = new PeerAddress(
                    Number160.createHash(BOOTSTRAP_NODE.getId()),
                    BOOTSTRAP_NODE.getIp(), BOOTSTRAP_NODE.getPort(), BOOTSTRAP_NODE.getPort());
        } catch (UnknownHostException ex) {
            throw new RuntimeException(BOOTSTRAP_NODE.toString(), ex);
        }
    }

    // If cache is used tests get faster as it doesn't create and bootstrap a new node at every test.
    // Need to observe if it can have some side effects.
    private static final boolean CACHE_CLIENTS = true;

    // Use that to stress test with repeated run of the test method body
    private static final int STRESS_TEST_LOOP_COUNT = 1;

    // need to be static to keep them during tests
    private final static Map<String, Peer> cachedPeers = new HashMap<>();

    private PeerDHT peer1DHT;
    private PeerDHT peer2DHT;
    private int client1Port;
    private int client2Port;
    private ConnectionType resolvedConnectionType;

    @Before
    public void configure() {
        client1Port = getNewRandomPort();
        client2Port = getNewRandomPort();
    }

    @After
    public void shutdown() {
        if (!CACHE_CLIENTS) {
            if (peer1DHT != null)
                peer1DHT.shutdown().awaitUninterruptibly();
            if (peer2DHT != null)
                peer2DHT.shutdown().awaitUninterruptibly();
        }
    }

    @Test
    public void bootstrapInUnknownMode() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            if (FORCED_CONNECTION_TYPE == ConnectionType.UNKNOWN)
                assertNotNull(bootstrapInUnknownMode("node_1", client1Port));

            shutdown();
        }
    }

    @Test
    public void testBootstrapDirectConnection() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            if (FORCED_CONNECTION_TYPE == ConnectionType.DIRECT)
                assertNotNull(bootstrapDirectConnection("node_1", client1Port));

            shutdown();
        }
    }

    @Test
    public void testBootstrapWithPortForwarding() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            if (FORCED_CONNECTION_TYPE == ConnectionType.NAT)
                assertNotNull(bootstrapWithPortForwarding("node_1", client1Port));

            shutdown();
        }
    }

    @Test
    public void testBootstrapInRelayMode() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            if (FORCED_CONNECTION_TYPE == ConnectionType.RELAY)
                assertNotNull(bootstrapInRelayMode("node_1", client1Port));

            shutdown();
        }
    }

    @Test
    public void testPut() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();

            peer1DHT = getDHTPeer("node_1", client1Port);
            FuturePut futurePut = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo")).start();
            futurePut.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futurePut.isSuccess());

            shutdown();
        }
    }

    @Test
    public void testPutGet() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            peer1DHT = getDHTPeer("node_1", client1Port);
            FuturePut futurePut = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo")).start();
            futurePut.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futurePut.isSuccess());


            peer2DHT = getDHTPeer("node_2", client2Port);
            FutureGet futureGet = peer2DHT.get(Number160.createHash("key")).start();
            futureGet.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futureGet.isSuccess());
            assertEquals("hallo", futureGet.data().object());

            shutdown();
        }
    }

    @Test
    public void testAdd() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            peer1DHT = getDHTPeer("node_1", client1Port);
            FuturePut futurePut1 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo1")).start();
            futurePut1.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futurePut1.isSuccess());

            FuturePut futurePut2 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo2")).start();
            futurePut2.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futurePut2.isSuccess());

            shutdown();
        }
    }

    @Test
    public void testAddGet() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            peer1DHT = getDHTPeer("node_1", client1Port);
            FuturePut futurePut1 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo1")).start();
            futurePut1.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futurePut1.isSuccess());

            FuturePut futurePut2 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo2")).start();
            futurePut2.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futurePut2.isSuccess());


            peer2DHT = getDHTPeer("node_2", client2Port);
            FutureGet futureGet = peer2DHT.get(Number160.createHash("locationKey")).all().start();
            futureGet.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futureGet.isSuccess());

            assertTrue(futureGet.dataMap().values().contains(new Data("hallo1")));
            assertTrue(futureGet.dataMap().values().contains(new Data("hallo2")));
            assertTrue(futureGet.dataMap().values().size() == 2);

            shutdown();
        }
    }

    @Test
    public void testAddRemove() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            peer1DHT = getDHTPeer("node_1", client1Port);
            FuturePut futurePut1 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo1")).start();
            futurePut1.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futurePut1.isSuccess());

            FuturePut futurePut2 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo2")).start();
            futurePut2.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futurePut2.isSuccess());


            peer2DHT = getDHTPeer("node_2", client2Port);
            Number160 contentKey = new Data("hallo1").hash();
            FutureRemove futureRemove = peer2DHT.remove(Number160.createHash("locationKey")).contentKey(contentKey)
                    .start();
            futureRemove.awaitUninterruptibly();

            // That fails sometimes in direct mode and NAT
            if (shouldEvaluateSuccess())
                assertTrue(futureRemove.isSuccess());

            FutureGet futureGet = peer2DHT.get(Number160.createHash("locationKey")).all().start();
            futureGet.awaitUninterruptibly();
            if (shouldEvaluateSuccess())
                assertTrue(futureGet.isSuccess());

            assertTrue(futureGet.dataMap().values().contains(new Data("hallo2")));
            assertTrue(futureGet.dataMap().values().size() == 1);

            shutdown();
        }
    }


    // The sendDirect operation fails in port forwarding mode because most routers does not support NAT reflections.
    // So if both clients are behind NAT they cannot send direct message to each other.
    // That will probably be fixed in a future version of TomP2P
    @Test
    public void testSendDirectBetweenLocalPeers() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            if (FORCED_CONNECTION_TYPE != ConnectionType.NAT && resolvedConnectionType != ConnectionType.NAT) {
                peer1DHT = getDHTPeer("node_1", client1Port);
                peer2DHT = getDHTPeer("node_2", client2Port);

                final CountDownLatch countDownLatch = new CountDownLatch(1);

                final StringBuilder result = new StringBuilder();
                peer2DHT.peer().objectDataReply((sender, request) -> {
                    countDownLatch.countDown();
                    result.append(String.valueOf(request));
                    return "pong";
                });
                FuturePeerConnection futurePeerConnection = peer1DHT.peer().createPeerConnection(peer2DHT.peer()
                        .peerAddress(), 500);
                FutureDirect futureDirect = peer1DHT.peer().sendDirect(futurePeerConnection).object("hallo").start();
                futureDirect.awaitUninterruptibly();


                countDownLatch.await(3, TimeUnit.SECONDS);
                if (countDownLatch.getCount() > 0)
                    Assert.fail("The test method did not complete successfully!");

                assertEquals("hallo", result.toString());
                assertTrue(futureDirect.isSuccess());
                log.debug(futureDirect.object().toString());
                assertEquals("pong", futureDirect.object());
            }

            shutdown();
        }
    }

    // That test should always succeed as we use the server seed node as receiver.
    // A node can send a message to another peer which is not in the same LAN.
    @Test
    public void testSendDirectToSeedNode() throws Exception {
        for (int i = 0; i < STRESS_TEST_LOOP_COUNT; i++) {
            configure();
            peer1DHT = getDHTPeer("node_1", client1Port);

            FuturePeerConnection futurePeerConnection =
                    peer1DHT.peer().createPeerConnection(BOOTSTRAP_NODE_ADDRESS, 500);
            FutureDirect futureDirect = peer1DHT.peer().sendDirect(futurePeerConnection).object("hallo").start();
            futureDirect.awaitUninterruptibly();
            assertTrue(futureDirect.isSuccess());
            assertEquals("pong", futureDirect.object());

            shutdown();
        }
    }


    private Peer bootstrapDirectConnection(String clientId, int clientPort) {
        final String id = clientId + clientPort;
        if (CACHE_CLIENTS && cachedPeers.containsKey(id)) {
            return cachedPeers.get(id);
        }
        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).start();
            FutureDiscover futureDiscover = peer.discover().peerAddress(BOOTSTRAP_NODE_ADDRESS).start();
            futureDiscover.awaitUninterruptibly();
            if (futureDiscover.isSuccess()) {
                log.info("Discover with direct connection successful. Address = " + futureDiscover.peerAddress());
                cachedPeers.put(id, peer);
                return peer;
            }
            else {
                log.warn("Discover with direct connection failed. Reason = " + futureDiscover.failedReason());
                peer.shutdown().awaitUninterruptibly();
                return null;
            }
        } catch (IOException e) {
            log.warn("Discover with direct connection failed. Exception = " + e.getMessage());
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();

            e.printStackTrace();
            return null;
        }
    }

    private Peer bootstrapWithPortForwarding(String clientId, int clientPort) {
        final String id = clientId + clientPort;
        if (CACHE_CLIENTS && cachedPeers.containsKey(id)) {
            return cachedPeers.get(id);
        }
        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
            PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
            FutureDiscover futureDiscover = peer.discover().peerAddress(BOOTSTRAP_NODE_ADDRESS).start();
            FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
            futureNAT.awaitUninterruptibly();
            if (futureNAT.isSuccess()) {
                log.info("Automatic port forwarding is setup. Now we do a futureDiscover again. Address = " +
                        futureNAT.peerAddress());
                futureDiscover = peer.discover().peerAddress(BOOTSTRAP_NODE_ADDRESS).start();
                futureDiscover.awaitUninterruptibly();
                if (futureDiscover.isSuccess()) {
                    log.info("Discover with automatic port forwarding was successful. Address = " + futureDiscover
                            .peerAddress());
                    cachedPeers.put(id, peer);
                    return peer;
                }
                else {
                    log.warn("Discover with automatic port forwarding failed. Reason = " + futureDiscover
                            .failedReason());
                    peer.shutdown().awaitUninterruptibly();
                    return null;
                }
            }
            else {
                log.warn("StartSetupPortforwarding failed. Reason = " + futureNAT
                        .failedReason());
                peer.shutdown().awaitUninterruptibly();
                return null;
            }
        } catch (IOException e) {
            log.warn("Discover with automatic port forwarding failed. Exception = " + e.getMessage());
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();

            e.printStackTrace();
            return null;
        }
    }

    private Peer bootstrapInRelayMode(String clientId, int clientPort) {
        final String id = clientId + clientPort;
        if (CACHE_CLIENTS && cachedPeers.containsKey(id)) {
            return cachedPeers.get(id);
        }

        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
            PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
            FutureDiscover futureDiscover = peer.discover().peerAddress(BOOTSTRAP_NODE_ADDRESS).start();
            FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
            FutureRelayNAT futureRelayNAT = peerNAT.startRelay(futureDiscover, futureNAT);
            futureRelayNAT.awaitUninterruptibly();
            if (futureRelayNAT.isSuccess()) {
                log.info("Bootstrap using relay was successful. Address = " + peer.peerAddress());
                cachedPeers.put(id, peer);
                return peer;

            }
            else {
                log.error("Bootstrap using relay failed " + futureRelayNAT.failedReason());
                futureRelayNAT.shutdown();
                peer.shutdown().awaitUninterruptibly();
                return null;
            }
        } catch (IOException e) {
            log.error("Bootstrap using relay failed. Exception " + e.getMessage());
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();

            e.printStackTrace();
            return null;
        }
    }

    private Peer bootstrapInUnknownMode(String clientId, int clientPort) {
        resolvedConnectionType = ConnectionType.DIRECT;
        Peer peer = bootstrapDirectConnection(clientId, clientPort);
        if (peer != null)
            return peer;

        resolvedConnectionType = ConnectionType.NAT;
        peer = bootstrapWithPortForwarding(clientId, clientPort);
        if (peer != null)
            return peer;

        resolvedConnectionType = ConnectionType.RELAY;
        peer = bootstrapInRelayMode(clientId, clientPort);
        if (peer != null)
            return peer;
        else
            log.error("Bootstrapping in all modes failed. Is bootstrap node " + BOOTSTRAP_NODE + "running?");

        resolvedConnectionType = null;
        return peer;
    }

    private PeerDHT getDHTPeer(String clientId, int clientPort) {
        Peer peer;
        if (FORCED_CONNECTION_TYPE == ConnectionType.DIRECT) {
            peer = bootstrapDirectConnection(clientId, clientPort);
        }
        else if (FORCED_CONNECTION_TYPE == ConnectionType.NAT) {
            peer = bootstrapWithPortForwarding(clientId, clientPort);
        }
        else if (FORCED_CONNECTION_TYPE == ConnectionType.RELAY) {
            peer = bootstrapInRelayMode(clientId, clientPort);
        }
        else {
            peer = bootstrapInUnknownMode(clientId, clientPort);
        }

        if (peer == null)
            Assert.fail("Bootstrapping failed." +
                    " forcedConnectionType= " + FORCED_CONNECTION_TYPE +
                    " resolvedConnectionType= " + resolvedConnectionType + "." +
                    " Is bootstrap node " + BOOTSTRAP_NODE + "running?");

        return new PeerBuilderDHT(peer).start();
    }

    private int getNewRandomPort() {
        // new Ports().tcpPort() returns a random port
        int newPort = new Ports().tcpPort();
        while (newPort == client1Port || newPort == client2Port)
            newPort = new Ports().tcpPort();

        return newPort;
    }

    private boolean shouldEvaluateSuccess() {
        return FORCED_CONNECTION_TYPE == ConnectionType.NAT || resolvedConnectionType == ConnectionType.NAT;
    }
}

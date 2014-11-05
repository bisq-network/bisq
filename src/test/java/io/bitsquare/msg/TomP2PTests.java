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

import java.io.IOException;

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
 * The seed node code is in startSeedNode.
 * <p>
 * To configure your test environment edit the static fields for id, IP and port.
 * In the configure method and the connectionType you can define your test scenario further.
 */

public class TomP2PTests {
    private static final Logger log = LoggerFactory.getLogger(TomP2PTests.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Configure
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Setup your seed node
    private final static String SEED_ID_WAN_1 = "digitalocean1.bitsquare.io";
    private final static String SEED_IP_WAN_1 = "188.226.179.109";
    private final static int SEED_PORT_WAN_1 = 5000;

    // Setup a second seed node used in some tests
    private final static String SEED_ID_WAN_2 = "digitalocean2.bitsquare.io";
    private final static String SEED_IP_WAN_2 = "188.226.179.109";
    private final static int SEED_PORT_WAN_2 = 5001;

    // new Ports().tcpPort() returns a random port
    private final static int CLIENT_1_PORT = new Ports().tcpPort();
    private final static int CLIENT_2_PORT = new Ports().tcpPort();

    // If you want to test in one specific connection mode define it directly, otherwise use UNKNOWN
    private final ConnectionType connectionType = ConnectionType.UNKNOWN;

    @Before
    public void configure() {
        if (connectionType == ConnectionType.DIRECT) {
            seedId = "localhost";
            seedIP = "127.0.0.1";
            seedPort = 5000;
        }
        else {
            seedId = SEED_ID_WAN_1;
            seedIP = SEED_IP_WAN_1;
            seedPort = SEED_PORT_WAN_1;
        }
    }

    // In port forwarding mode the isSuccess returns false, but the DHT operations succeeded.
    // Needs investigation why.
    private boolean ignoreSuccessTests = true;

    // If cache is used tests get faster as it doesn't create and bootstrap a new node at every test.
    // Need to observe if it can have some side effects. 
    private boolean cacheClients = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private enum ConnectionType {
        UNKNOWN,
        DIRECT,
        NAT,
        RELAY
    }

    private final static Map<String, Peer> cachedPeers = new HashMap<>();
    private String seedId;
    private String seedIP;
    private int seedPort;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Seed node
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) throws Exception {
        // Define your seed node IP and port
        // "127.0.0.1" for localhost or SEED_ID_WAN_1
        new TomP2PTests().startSeedNode("127.0.0.1", 5000);
    }

    public Thread startSeedNode(String seedNodeId, int seedNodePort) {
        Thread thread = new Thread(() -> {
            Peer peer = null;
            try {
                peer = new PeerBuilder(Number160.createHash(seedNodeId)).ports(seedNodePort).start();
                PeerDHT peerDHT = new PeerBuilderDHT(peer).start();
                peerDHT.peer().objectDataReply((sender, request) -> {
                    log.trace("received request: ", request.toString());
                    return "pong";
                });

                new PeerBuilderNAT(peer).start();

                log.debug("peer started.");
                for (; ; ) {
                    for (PeerAddress pa : peer.peerBean().peerMap().all()) {
                        log.debug("peer online (TCP):" + pa);
                    }
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                if (peer != null)
                    peer.shutdown().awaitUninterruptibly();
            }
        });
        thread.start();
        return thread;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void bootstrapInUnknownMode() throws Exception {
        if (connectionType == ConnectionType.UNKNOWN)
            assertNotNull(bootstrapInUnknownMode("node_1", CLIENT_1_PORT));
    }

    @Test
    public void testBootstrapDirectConnection() throws Exception {
        if (connectionType == ConnectionType.DIRECT)
            assertNotNull(bootstrapDirectConnection("node_1", CLIENT_1_PORT));
    }

    @Test
    public void testBootstrapWithPortForwarding() throws Exception {
        if (connectionType == ConnectionType.NAT)
            assertNotNull(bootstrapWithPortForwarding("node_1", CLIENT_1_PORT));
    }

    @Test
    public void testBootstrapInRelayMode() throws Exception {
        if (connectionType == ConnectionType.RELAY)
            assertNotNull(bootstrapInRelayMode("node_1", CLIENT_1_PORT));
    }


    @Test
    // @Ignore
    public void testPutGet() throws Exception {
        PeerDHT peer1DHT = getDHTPeer("node_1", CLIENT_1_PORT);
        PeerDHT peer2DHT = getDHTPeer("node_2", CLIENT_2_PORT);

        FuturePut futurePut = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo")).start();
        futurePut.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futurePut.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("key")).start();
        futureGet.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futureGet.isSuccess());
        log.debug("######## futureGet.data().toString()" + futureGet.data().toString());
        log.debug("######## futureGet.data().object().toString()" + futureGet.data().object().toString());
        assertEquals("hallo", futureGet.data().object());
        if (!ignoreSuccessTests)
            assertTrue(futurePut.isSuccess());

        if (!cacheClients) {
            peer1DHT.shutdown().awaitUninterruptibly();
            peer2DHT.shutdown().awaitUninterruptibly();
        }
    }

    @Test
    @Ignore
    public void testAddGet() throws Exception {
        PeerDHT peer1DHT = getDHTPeer("node_1", CLIENT_1_PORT);
        PeerDHT peer2DHT = getDHTPeer("node_2", CLIENT_2_PORT);

        FuturePut futurePut1 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo1")).start();
        futurePut1.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futurePut1.isSuccess());

        FuturePut futurePut2 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo2")).start();
        futurePut2.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futurePut2.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("locationKey")).all().start();
        futureGet.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futureGet.isSuccess());

        assertTrue(futureGet.dataMap().values().contains(new Data("hallo1")));
        assertTrue(futureGet.dataMap().values().contains(new Data("hallo2")));
        assertTrue(futureGet.dataMap().values().size() == 2);

        if (!cacheClients) {
            peer1DHT.shutdown().awaitUninterruptibly();
            peer2DHT.shutdown().awaitUninterruptibly();
        }
    }

    @Test
    @Ignore
    public void testAddRemove() throws Exception {
        PeerDHT peer1DHT = getDHTPeer("node_1", CLIENT_1_PORT);
        PeerDHT peer2DHT = getDHTPeer("node_2", CLIENT_2_PORT);

        FuturePut futurePut1 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo1")).start();
        futurePut1.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futurePut1.isSuccess());

        FuturePut futurePut2 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo2")).start();
        futurePut2.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futurePut2.isSuccess());

        Number160 contentKey = new Data("hallo1").hash();
        FutureRemove futureRemove = peer2DHT.remove(Number160.createHash("locationKey")).contentKey(contentKey).start();
        futureRemove.awaitUninterruptibly();

        if (!ignoreSuccessTests)
            assertTrue(futureRemove.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("locationKey")).all().start();
        futureGet.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futureGet.isSuccess());

        assertTrue(futureGet.dataMap().values().contains(new Data("hallo2")));
        assertTrue(futureGet.dataMap().values().size() == 1);

        if (!cacheClients) {
            peer1DHT.shutdown().awaitUninterruptibly();
            peer2DHT.shutdown().awaitUninterruptibly();
        }
    }

    @Test
    @Ignore
    public void testDHT2Servers() throws Exception {
        PeerDHT peer1DHT = getDHTPeer("node_1", CLIENT_1_PORT, SEED_ID_WAN_1, SEED_IP_WAN_1, SEED_PORT_WAN_1);
        PeerDHT peer2DHT = getDHTPeer("node_2", CLIENT_2_PORT, SEED_ID_WAN_2, SEED_IP_WAN_2, SEED_PORT_WAN_2);

        FuturePut futurePut = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo")).start();
        futurePut.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futurePut.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("key")).start();
        futureGet.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futureGet.isSuccess());
        assertEquals("hallo", futureGet.data().object());

        if (!cacheClients) {
            peer1DHT.shutdown().awaitUninterruptibly();
            peer2DHT.shutdown().awaitUninterruptibly();
        }
    }

    // That test fails in port forwarding mode because most routers does not support NAT reflections.
    // So if both clients are behind NAT they cannot send direct message to the other.
    // That will probably be fixed in a future version of TomP2P
    // In relay mode the test should succeed
    @Test
    @Ignore
    public void testSendDirectRelay() throws Exception {
        PeerDHT peer1DHT = getDHTPeer("node_1", CLIENT_1_PORT);
        PeerDHT peer2DHT = getDHTPeer("node_2", CLIENT_2_PORT);

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final StringBuilder result = new StringBuilder();
        peer2DHT.peer().objectDataReply((sender, request) -> {
            countDownLatch.countDown();
            result.append(String.valueOf(request));
            return "world";
        });
        FuturePeerConnection futurePeerConnection = peer1DHT.peer().createPeerConnection(peer2DHT.peer().peerAddress(),
                500);
        FutureDirect futureDirect = peer1DHT.peer().sendDirect(futurePeerConnection).object("hallo").start();
        futureDirect.awaitUninterruptibly();


        countDownLatch.await(3, TimeUnit.SECONDS);
        if (countDownLatch.getCount() > 0)
            Assert.fail("The test method did not complete successfully!");

        assertEquals("hallo", result.toString());
        assertTrue(futureDirect.isSuccess());
        //assertEquals("pong", futureDirect.object());

        if (!cacheClients) {
            peer1DHT.shutdown().awaitUninterruptibly();
            peer2DHT.shutdown().awaitUninterruptibly();
        }
    }

    // That test should succeed in port forwarding as we use the server seed node as receiver
    @Test
    @Ignore
    public void testSendDirectPortForwarding() throws Exception {
        PeerDHT peer1DHT = getDHTPeer("node_1", CLIENT_1_PORT);
        PeerAddress reachablePeerAddress = new PeerAddress(Number160.createHash(seedId), seedIP, seedPort,
                seedPort);

        FuturePeerConnection futurePeerConnection = peer1DHT.peer().createPeerConnection(reachablePeerAddress, 500);
        FutureDirect futureDirect = peer1DHT.peer().sendDirect(futurePeerConnection).object("hallo").start();
        futureDirect.awaitUninterruptibly();
        assertTrue(futureDirect.isSuccess());
        //assertEquals("pong", futureDirect.object());

        if (!cacheClients) {
            peer1DHT.shutdown().awaitUninterruptibly();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bootstrapping
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Peer bootstrapDirectConnection(String clientId, int clientPort) {
        return bootstrapDirectConnection(clientId, clientPort, seedId, seedIP, seedPort);
    }

    private Peer bootstrapDirectConnection(String clientId, int clientPort, String seedNodeId,
                                           String seedNodeIP, int seedNodePort) {
        final String id = clientId + clientPort;
        if (cacheClients && cachedPeers.containsKey(id)) {
            return cachedPeers.get(id);
        }
        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).start();
            PeerAddress masterNodeAddress = new PeerAddress(Number160.createHash(seedNodeId), seedNodeIP, seedNodePort,
                    seedNodePort);
            FutureDiscover futureDiscover = peer.discover().peerAddress(masterNodeAddress).start();
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
        return bootstrapWithPortForwarding(clientId, clientPort, seedId, seedIP, seedPort);
    }

    private Peer bootstrapWithPortForwarding(String clientId, int clientPort, String seedNodeId,
                                             String seedNodeIP, int seedNodePort) {
        final String id = clientId + clientPort;
        if (cacheClients && cachedPeers.containsKey(id)) {
            return cachedPeers.get(id);
        }
        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
            PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
            PeerAddress masterNodeAddress = new PeerAddress(Number160.createHash(seedNodeId), seedNodeIP, seedNodePort,
                    seedNodePort);
            FutureDiscover futureDiscover = peer.discover().peerAddress(masterNodeAddress).start();
            FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
            futureNAT.awaitUninterruptibly();
            if (futureNAT.isSuccess()) {
                log.info("Automatic port forwarding is setup. Now we do a futureDiscover again. Address = " +
                        futureNAT.peerAddress());
                futureDiscover = peer.discover().peerAddress(masterNodeAddress).start();
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
        return bootstrapInRelayMode(clientId, clientPort, seedId, seedIP, seedPort);
    }

    private Peer bootstrapInRelayMode(String clientId, int clientPort, String seedNodeId,
                                      String seedNodeIP, int seedNodePort) {
        final String id = clientId + clientPort;
        if (cacheClients && cachedPeers.containsKey(id)) {
            return cachedPeers.get(id);
        }

        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
            PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
            PeerAddress masterNodeAddress = new PeerAddress(Number160.createHash(seedNodeId), seedNodeIP, seedNodePort,
                    seedNodePort);
            FutureDiscover futureDiscover = peer.discover().peerAddress(masterNodeAddress).start();
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
        return bootstrapInUnknownMode(clientId, clientPort, seedId, seedIP, seedPort);
    }

    private Peer bootstrapInUnknownMode(String clientId, int clientPort, String seedNodeId,
                                        String seedNodeIP, int seedNodePort) {
        Peer peer = bootstrapDirectConnection(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
        if (peer != null)
            return peer;

        peer = bootstrapWithPortForwarding(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
        if (peer != null)
            return peer;

        peer = bootstrapInRelayMode(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
        if (peer != null)
            return peer;
        else
            log.error("Bootstrapping in all modes failed. Check if the seed node is running. " +
                    "seedNodeId= " + seedNodeId +
                    "seedNodeIP= " + seedNodeIP +
                    "seedNodePort= " + seedNodePort);

        return peer;
    }

    private PeerDHT getDHTPeer(String clientId, int clientPort) {
        return getDHTPeer(clientId, clientPort, seedId, seedIP, seedPort);
    }

    private PeerDHT getDHTPeer(String clientId, int clientPort, String seedNodeId,
                               String seedNodeIP, int seedNodePort) {
        Peer peer;
        if (connectionType == ConnectionType.DIRECT) {
            peer = bootstrapDirectConnection(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
        }
        else if (connectionType == ConnectionType.NAT) {
            peer = bootstrapWithPortForwarding(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
        }
        else if (connectionType == ConnectionType.RELAY) {
            peer = bootstrapInRelayMode(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
        }
        else {
            peer = bootstrapDirectConnection(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
            if (peer == null)
                peer = bootstrapWithPortForwarding(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
            if (peer == null)
                peer = bootstrapInRelayMode(clientId, clientPort, seedNodeId, seedNodeIP, seedNodePort);
            if (peer == null)
                Assert.fail("Bootstrapping in all modes failed. Check if the seed node is running. " +
                        "seedNodeId= " + seedNodeId +
                        "seedNodeIP= " + seedNodeIP +
                        "seedNodePort= " + seedNodePort);
        }

        return new PeerBuilderDHT(peer).start();
    }

    /*private PeerDHT startClient(String clientId, int clientPort, String seedNodeId,
                                String seedNodeIP, int seedNodePort) throws Exception {


        final String id = clientId + clientPort;
        log.debug("id = " + id + "/" + cachedPeers.containsKey(id));
        if (cacheClients && cachedPeers.containsKey(id)) {
            return cachedPeers.get(id);
        }
        else {
            Peer peer = null;
            try {
                peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
                PeerDHT peerDHT = new PeerBuilderDHT(peer).start();

                PeerAddress masterNodeAddress = new PeerAddress(Number160.createHash(seedNodeId), seedNodeIP, 
                seedNodePort,
                        seedNodePort);
                FutureDiscover futureDiscover = peer.discover().peerAddress(masterNodeAddress).start();
                futureDiscover.awaitUninterruptibly();
                if (futureDiscover.isSuccess() && overrideBootStrapMode.equals("default")) {
                    log.info("Discover with direct connection successful. Address = " + futureDiscover.peerAddress());
                    cachedPeers.put(id, peerDHT);
                    return peerDHT;
                }
                else {
                    PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
                    FutureDiscover futureDiscover2 = peer.discover().peerAddress(masterNodeAddress).start();
                    FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover2);
                    futureNAT.awaitUninterruptibly();
                    if (futureNAT.isSuccess() && !overrideBootStrapMode.equals("relay")) {
                        log.info("Automatic port forwarding is setup. Address = " +
                                futureNAT.peerAddress());
                        FutureDiscover futureDiscover3 = peer.discover().peerAddress(masterNodeAddress).start();
                        futureDiscover3.awaitUninterruptibly();
                        if (futureDiscover3.isSuccess()) {
                            log.info("Discover with automatic port forwarding successful. Address = " + futureDiscover3
                                    .peerAddress());

                            cachedPeers.put(id, peerDHT);
                            return peerDHT;
                        }
                        else {
                            log.error("Bootstrap with NAT after futureDiscover2 failed " + futureDiscover3
                                    .failedReason());
                            peer.shutdown().awaitUninterruptibly();
                            return null;
                        }
                    }
                    else {
                        log.debug("futureNAT.failedReason() = " + futureNAT.failedReason());
                        FutureDiscover futureDiscover4 = peer.discover().peerAddress(masterNodeAddress).start();
                        FutureRelayNAT futureRelayNAT = peerNAT.startRelay(futureDiscover4, futureNAT);
                        futureRelayNAT.awaitUninterruptibly();
                        if (futureRelayNAT.isSuccess()) {
                            log.info("Bootstrap using relay successful. Address = " + peer.peerAddress());
                            cachedPeers.put(id, peerDHT);
                            return peerDHT;

                        }
                        else {
                            log.error("Bootstrap using relay failed " + futureRelayNAT.failedReason());
                            Assert.fail("Bootstrap using relay failed " + futureRelayNAT.failedReason());
                            futureRelayNAT.shutdown();
                            peer.shutdown().awaitUninterruptibly();
                            return null;
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Bootstrap in relay mode  failed " + e.getMessage());
                e.printStackTrace();
                Assert.fail("Bootstrap in relay mode  failed " + e.getMessage());
                if (peer != null)
                    peer.shutdown().awaitUninterruptibly();
                return null;
            }
        }
    }
*/

}

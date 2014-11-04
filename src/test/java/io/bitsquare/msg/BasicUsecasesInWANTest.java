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
import net.tomp2p.dht.StorageLayer;
import net.tomp2p.dht.StorageMemory;
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
import org.junit.Ignore;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * Test bootstrapping, put/get/add/remove and sendDirect in WAN environment (auto port forwarding, Relay)
 * startBootstrappingSeedNode is used as the server side code
 */
public class BasicUsecasesInWANTest {
    private static final Logger log = LoggerFactory.getLogger(BasicUsecasesInWANTest.class);

    private final static String SERVER_ID_1 = "digitalocean1.bitsquare.io"; // Manfreds server
    private final static String SERVER_IP_1 = "188.226.179.109"; // Manfreds server
    private final static int SERVER_PORT_1 = 5000;

    private final static String SERVER_ID_2 = "digitalocean2.bitsquare.io";  // Steve's server
    //private final static String SERVER_IP_2 = "128.199.251.106"; // Steve's server
    private final static String SERVER_IP_2 = "188.226.179.109"; // Manfreds server
    private final static int SERVER_PORT_2 = 5001;


    private final static String SERVER_ID = SERVER_ID_1;
    private final static String SERVER_IP = SERVER_IP_1;
    private final static int SERVER_PORT = SERVER_PORT_1;


    private final static String CLIENT_1_ID = "alice";
    private final static String CLIENT_2_ID = "bob";
    private final static int CLIENT_1_PORT = new Ports().tcpPort();
    private final static int CLIENT_2_PORT = new Ports().tcpPort();

    private String overrideBootStrapMode = "default"; // nat, relay

    // In port forwarding mode the isSuccess returns false, but the DHT operations succeeded.
    // Needs investigation why.
    private boolean ignoreSuccessTests = true;

    // Don't create and bootstrap the nodes at every test but reuse already created ones.
    private boolean cacheClients = true;

    private final static Map<String, PeerDHT> clients = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Seed node
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) throws Exception {
        new BasicUsecasesInWANTest().startBootstrappingSeedNode();
    }

    public void startBootstrappingSeedNode() {
        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(SERVER_ID_1)).ports(SERVER_PORT_1).start();
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
            e.printStackTrace();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @Ignore
    public void testPutGet() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, CLIENT_2_PORT);

        FuturePut futurePut = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo")).start();
        futurePut.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futurePut.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("key")).start();
        futureGet.awaitUninterruptibly();
        if (!ignoreSuccessTests)
            assertTrue(futureGet.isSuccess());
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
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, CLIENT_2_PORT);

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
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, CLIENT_2_PORT);

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
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT, SERVER_ID_1, SERVER_IP_1, SERVER_PORT_1);
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, CLIENT_2_PORT, SERVER_ID_2, SERVER_IP_2, SERVER_PORT_2);

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
        overrideBootStrapMode = "relay";
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        overrideBootStrapMode = "nat";
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, CLIENT_2_PORT);

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
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        PeerAddress reachablePeerAddress = new PeerAddress(Number160.createHash(SERVER_ID), SERVER_IP, SERVER_PORT,
                SERVER_PORT);

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
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////


    private PeerDHT startClient(String clientId, int clientPort) throws Exception {
        return startClient(clientId, clientPort, SERVER_ID, SERVER_IP, SERVER_PORT);
    }

    private PeerDHT startClient(String clientId, int clientPort, String serverId,
                                String serverIP, int serverPort) throws Exception {
        final String id = clientId + clientPort;
        log.debug("id = " + id + "/" + clients.containsKey(id));
        if (cacheClients && clients.containsKey(id)) {
            return clients.get(id);
        }
        else {
            Peer peer = null;
            try {
                peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
                PeerDHT peerDHT = new PeerBuilderDHT(peer).storageLayer(new StorageLayer(new StorageMemory())).start();

                PeerAddress masterNodeAddress = new PeerAddress(Number160.createHash(serverId), serverIP, serverPort,
                        serverPort);
                FutureDiscover futureDiscover = peer.discover().peerAddress(masterNodeAddress).start();
                futureDiscover.awaitUninterruptibly();
                if (futureDiscover.isSuccess() && overrideBootStrapMode.equals("default")) {
                    log.info("Discover with direct connection successful. Address = " + futureDiscover.peerAddress());
                    clients.put(id, peerDHT);
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

                            clients.put(id, peerDHT);
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
                            clients.put(id, peerDHT);
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


}

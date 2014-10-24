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

import net.tomp2p.connection.Ports;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageLayer;
import net.tomp2p.dht.StorageMemory;
import net.tomp2p.futures.BaseFutureAdapter;
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
 * Tests bootstrapping, put/get and sendDirect in WAN environment (auto port forwarding, Relay)
 * startBootstrappingSeedNode is used as the server side code
 */
public class BasicUsecasesInWANTest {
    private static final Logger log = LoggerFactory.getLogger(BasicUsecasesInWANTest.class);

    // update to external ip (whats my ip)
    private final static String CLIENT_IP = "83.36.8.117";

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

    @Test
    @Ignore
    public void testPutGet() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, new Ports().tcpPort());
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, new Ports().tcpPort());

        FuturePut futurePut = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo")).start();
        futurePut.awaitUninterruptibly();
        assertTrue(futurePut.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("key")).start();
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());
        assertEquals("hallo", futureGet.data().object());

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();
    }

    @Test
    @Ignore
    public void testAddGet() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, new Ports().tcpPort());
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, new Ports().tcpPort());

        FuturePut futurePut1 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo1")).start();
        futurePut1.awaitUninterruptibly();
        assertTrue(futurePut1.isSuccess());

        FuturePut futurePut2 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo2")).start();
        futurePut2.awaitUninterruptibly();
        assertTrue(futurePut2.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("locationKey")).all().start();
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());

        assertTrue(futureGet.dataMap().values().contains(new Data("hallo1")));
        assertTrue(futureGet.dataMap().values().contains(new Data("hallo2")));
        assertTrue(futureGet.dataMap().values().size() == 2);

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();
    }

    @Test
    @Ignore
    public void testRemove() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, new Ports().tcpPort());
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, new Ports().tcpPort());

        FuturePut futurePut1 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo1")).start();
        futurePut1.awaitUninterruptibly();
        assertTrue(futurePut1.isSuccess());

        FuturePut futurePut2 = peer1DHT.add(Number160.createHash("locationKey")).data(new Data("hallo2")).start();
        futurePut2.awaitUninterruptibly();
        assertTrue(futurePut2.isSuccess());

        Number160 contentKey = new Data("hallo1").hash();
        FutureRemove futureRemove = peer2DHT.remove(Number160.createHash("locationKey")).contentKey(contentKey).start();
        futureRemove.awaitUninterruptibly();

        // why is futureRemove.isSuccess() = false ?
        log.debug(futureRemove.failedReason());// Future (compl/canc):true/false, OK, Minimun number of results reached 
        // assertTrue(futureRemove.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("locationKey")).all().start();
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());

        assertTrue(futureGet.dataMap().values().contains(new Data("hallo2")));
        assertTrue(futureGet.dataMap().values().size() == 1);

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();
    }

    @Test
    @Ignore
    public void testDHT2Servers() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, new Ports().tcpPort(), SERVER_ID_1, SERVER_IP_1, SERVER_PORT_1);
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, new Ports().tcpPort(), SERVER_ID_2, SERVER_IP_2, SERVER_PORT_2);

        FuturePut futurePut = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo")).start();
        futurePut.awaitUninterruptibly();
        assertTrue(futurePut.isSuccess());

        FutureGet futureGet = peer2DHT.get(Number160.createHash("key")).start();
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());
        assertEquals("hallo", futureGet.data().object());

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();
    }

    @Test
    @Ignore
    public void testSendDirect() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, new Ports().tcpPort());
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, new Ports().tcpPort());

        final StringBuilder result = new StringBuilder();
        peer2DHT.peer().objectDataReply((sender, request) -> {
            result.append(String.valueOf(request));
            return request;
        });
        FuturePeerConnection futurePeerConnection = peer1DHT.peer().createPeerConnection(peer2DHT.peer().peerAddress(),
                500);
        FutureDirect futureDirect = peer1DHT.peer().sendDirect(futurePeerConnection).object("hallo").start();
        futureDirect.addListener(new BaseFutureAdapter<FutureDirect>() {
            @Override
            public void operationComplete(FutureDirect future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("isSuccess");
                }
                else {
                    log.debug("Failed");
                }
            }
        });

        futureDirect.awaitUninterruptibly();

        // futureDirect.object() causes a null pointer as buffer() in futureDirect is null and 
        // buffer().object(); is called.
        //assertEquals("hallo", futureDirect.object()); 

        assertEquals("hallo", result.toString());

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();
    }

    private PeerDHT startClient(String clientId, int clientPort) throws Exception {
        return startClient(clientId, clientPort, SERVER_ID, SERVER_IP, SERVER_PORT);
    }

    private PeerDHT startClient(String clientId, int clientPort, String serverId,
                                String serverIP, int serverPort) throws Exception {
        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
            PeerDHT peerDHT = new PeerBuilderDHT(peer).storageLayer(new StorageLayer(new StorageMemory())).start();

            PeerAddress masterNodeAddress = new PeerAddress(Number160.createHash(serverId), serverIP, serverPort,
                    serverPort);
            FutureDiscover futureDiscover = peer.discover().peerAddress(masterNodeAddress).start();
            futureDiscover.awaitUninterruptibly();
            if (futureDiscover.isSuccess()) {
                log.info("Discover with direct connection successful. Address = " + futureDiscover.peerAddress());
                return peerDHT;
            }
            else {
                PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
                FutureNAT futureNAT = peerNAT.startSetupPortforwarding(futureDiscover);
                futureNAT.awaitUninterruptibly();
                if (futureNAT.isSuccess()) {
                    log.info("Automatic port forwarding is setup. Address = " +
                            futureNAT.peerAddress());
                    FutureDiscover futureDiscover2 = peer.discover().peerAddress(masterNodeAddress).start();
                    futureDiscover2.awaitUninterruptibly();
                    if (futureDiscover2.isSuccess()) {
                        log.info("Discover with automatic port forwarding successful. Address = " + futureDiscover2
                                .peerAddress());

                        log.info("Automatic port forwarding is setup. Address = " +
                                futureNAT.peerAddress());
                        return peerDHT;
                    }
                    else {
                        log.error("Bootstrap with NAT after futureDiscover2 failed " + futureDiscover2.failedReason());
                        peer.shutdown().awaitUninterruptibly();
                        return null;
                    }
                }
                else {
                    FutureRelayNAT futureRelayNAT = peerNAT.startRelay(futureDiscover, futureNAT);
                    futureRelayNAT.awaitUninterruptibly();
                    if (futureRelayNAT.isSuccess()) {
                        log.info("Bootstrap using relay successful. Address = " + peer.peerAddress());
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

    // just for documentation
    public void startServer() {
        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash("digitalocean1.bitsquare.io")).ports(5000).start();
            new PeerBuilderDHT(peer).start();
            new PeerBuilderNAT(peer).start();

            System.out.println("peer started.");
            for (; ; ) {
                for (PeerAddress pa : peer.peerBean().peerMap().all()) {
                    System.out.println("peer online (TCP):" + pa);
                }
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();
            e.printStackTrace();
        }
    }

}

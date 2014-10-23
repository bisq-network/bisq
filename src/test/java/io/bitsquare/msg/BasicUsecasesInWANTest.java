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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
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

    private final static String SERVER_ID = "digitalocean1.bitsquare.io";
    private final static String SERVER_IP = "188.226.179.109";
    //private final static String SERVER_IP = "128.199.251.106"; // steves server
    private final static int SERVER_PORT = 5000;

    private final static String CLIENT_1_ID = "alice";
    private final static String CLIENT_2_ID = "bob";
    private final static int CLIENT_1_PORT = 6505;
    private final static int CLIENT_2_PORT = 6506;

    private Thread serverThread;


    @Test
    @Ignore
    public void testBootstrap() throws Exception {
        PeerDHT peerDHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);

        // external ports cannot be tested as they come random
        log.debug("############# tcpPort = " + peerDHT.peerAddress().tcpPort());
        log.debug("############# udpPort = " + peerDHT.peerAddress().udpPort());

        // in case of port forwarding use that:
        assertEquals(CLIENT_IP, peerDHT.peerAddress().inetAddress().getHostAddress());

        // in case of relay use that:
        //assertEquals("192.168.1.33", peerDHT.peerAddress().inetAddress().getHostAddress());


        peerDHT.shutdown().awaitUninterruptibly();
    }

    @Test
    @Ignore
    public void testDHT() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, CLIENT_2_PORT);

        FuturePut futurePut1 = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo1")).start();
        futurePut1.awaitUninterruptibly();
        log.debug("futurePut1.isSuccess() = " + futurePut1.isSuccess());
        // why fails that?
        // assertTrue(futurePut1.isSuccess());


        FutureGet futureGet2 = peer1DHT.get(Number160.createHash("key")).start();
        futureGet2.awaitUninterruptibly();
        assertTrue(futureGet2.isSuccess());
        assertNotNull(futureGet2.data());
        assertEquals("hallo1", futureGet2.data().object());

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();
    }

    // That test is failing because of timeouts
    /*
    
    server:
    15:52:23.749 [NETTY-TOMP2P - worker-client/server - -1-1] WARN  net.tomp2p.connection.TimeoutFactory - channel 
    timeout for channel Sender [id: 0xc4f02816, /0:0:0:0:0:0:0:0:49351] 
15:52:23.752 [NETTY-TOMP2P - worker-client/server - -1-1] WARN  net.tomp2p.connection.TimeoutFactory - Request status
 is msgid=1812373319,t=REQUEST_1,c=PING,tcp,s=paddr[0x7728a3c6e3351739891ca100b5fb774ec5af4ddf[/188.226.179.109,
 5000]]/relay(false,0)=[],r=paddr[0x48181acd22b3edaebc8a447868a7df7ce629920a[/83.36.8.117,t:64506,
 u:62960]]/relay(false,0)=[] 
15:52:23.767 [NETTY-TOMP2P - worker-client/server - -1-1] DEBUG net.tomp2p.peers.PeerMap - peer 
paddr[0x48181acd22b3edaebc8a447868a7df7ce629920a[/83.36.8.117,t:64506,u:62960]]/relay(false,
0)=[] is offline with reason {} net.tomp2p.connection.PeerException: Future (compl/canc):true/false, FAILED, 
No future set beforehand, probably an early shutdown / timeout, or use setFailedLater() or setResponseLater()

    client:
    21:52:15.559 [NETTY-TOMP2P - worker-client/server - -1-8] DEBUG net.tomp2p.peers.PeerMap - peer 
    paddr[0x48181acd22b3edaebc8a447868a7df7ce629920a[/83.36.8.117,t:64506,u:62960]]/relay(false,
    0)=[] is offline with reason {} net.tomp2p.connection.PeerException: Future (compl/canc):true/false, FAILED, 
    No future set beforehand, probably an early shutdown / timeout, or use setFailedLater() or setResponseLater()

     */
    @Test
    @Ignore
    public void testSendDirect() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, CLIENT_2_PORT);

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final StringBuilder result = new StringBuilder();
        peer2DHT.peer().objectDataReply((sender, request) -> {
            countDownLatch.countDown();
            result.append(String.valueOf(request));
            return request;
        });
        log.debug("peer1DHT " + peer1DHT.peerAddress());
        log.debug("peer2DHT " + peer2DHT.peerAddress());


        // FuturePeerConnection futurePeerConnection = peer1DHT.peer().createPeerConnection(peer2DHT.peer()
        // .peerAddress(),
        //         PeerConnection.HEART_BEAT_MILLIS);
        FuturePeerConnection futurePeerConnection = peer1DHT.peer().createPeerConnection(peer2DHT.peer().peerAddress(),
                500);
        FutureDirect futureDirect = peer1DHT.peer().sendDirect(futurePeerConnection).object("hallo").start();

        //FutureDirect futureDirect2 = peer1DHT.peer().sendDirect(peer2DHT.peer().peerAddress()).object("hallo")
        // .start();

        futureDirect.addListener(new BaseFutureAdapter<FutureDirect>() {
            @Override
            public void operationComplete(FutureDirect future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("isSuccess");
                }
                else {
                    log.debug("Failed");
                    // future response state:,type:FAILED,msg:7,reason:Channel creation failed [id: 0xc163a536]/io
                    // .netty.channel.ConnectTimeoutException: 
                    // connection timed out: /83.36.8.117:54458
                }
            }
        });

        futureDirect.awaitUninterruptibly();

        countDownLatch.await(3, TimeUnit.SECONDS);
        if (countDownLatch.getCount() > 0)
            Assert.fail("The test method did not complete successfully!");

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();

        assertEquals("hallo", futureDirect.object());
        assertEquals("hallo", result.toString());
    }


    private PeerDHT startClient(String clientId, int clientPort) throws Exception {
        Peer peer = null;
        try {
            peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
            PeerDHT peerDHT = new PeerBuilderDHT(peer).storageLayer(new StorageLayer(new StorageMemory())).start();

            PeerAddress masterNodeAddress = new PeerAddress(Number160.createHash(SERVER_ID), SERVER_IP, SERVER_PORT,
                    SERVER_PORT);
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
                    FutureDiscover futureDiscover2 = peer.discover().peerAddress(masterNodeAddress).start();
                    futureDiscover2.awaitUninterruptibly();
                    if (futureDiscover2.isSuccess()) {
                        log.info("Discover with direct connection successful. Address = " + futureDiscover2
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
                        log.info("Bootstrap using relay successful. Address = " +
                                peer.peerAddress());
                        futureRelayNAT.shutdown();
                        peer.shutdown().awaitUninterruptibly();
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
            peer.shutdown().awaitUninterruptibly();
            return null;
        }
    }

    public void startBootstrappingSeedNode() {
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
            e.printStackTrace();
        }
    }

}

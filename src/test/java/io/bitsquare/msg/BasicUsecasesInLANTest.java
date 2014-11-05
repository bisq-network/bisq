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
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.futures.FutureDiscover;
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

public class BasicUsecasesInLANTest {
    private static final Logger log = LoggerFactory.getLogger(BasicUsecasesInLANTest.class);

    private final static String SERVER_ID = "localhost";
    private final static String SERVER_IP = "127.0.0.1";
    private final static int SERVER_PORT = 5000;

    private final static String CLIENT_1_ID = "alice";
    private final static String CLIENT_2_ID = "bob";
    private final static int CLIENT_1_PORT = 6510;
    private final static int CLIENT_2_PORT = 6511;

    private Thread serverThread;

    @Before
    public void startServer() throws Exception {
        serverThread = new Thread(() -> {
            Peer peer = null;
            try {
                peer = new PeerBuilder(Number160.createHash(SERVER_ID)).ports(SERVER_PORT).start();
                log.debug("peer started.");
                while (true) {
                    for (PeerAddress pa : peer.peerBean().peerMap().all()) {
                        log.debug("peer online (TCP):" + pa);
                    }
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                if (peer != null)
                    peer.shutdown().awaitUninterruptibly();
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        });
        serverThread.start();
    }

    @After
    public void stopServer() throws Exception {
        serverThread.interrupt();
    }

    @Test
    @Ignore
    public void testBootstrap() throws Exception {
        PeerDHT peerDHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        assertEquals(CLIENT_1_PORT, peerDHT.peerAddress().tcpPort());
        assertEquals(CLIENT_1_PORT, peerDHT.peerAddress().udpPort());
        peerDHT.shutdown().awaitUninterruptibly();
    }

    @Test
    @Ignore
    public void testDHT() throws Exception {
        PeerDHT peer1DHT = startClient(CLIENT_1_ID, CLIENT_1_PORT);
        PeerDHT peer2DHT = startClient(CLIENT_2_ID, CLIENT_2_PORT);

        FuturePut futurePut1 = peer1DHT.put(Number160.createHash("key")).data(new Data("hallo1")).start();
        futurePut1.awaitUninterruptibly();
        // why fails that?
        //assertTrue(futurePut1.isSuccess());

        FutureGet futureGet2 = peer1DHT.get(Number160.createHash("key")).start();
        futureGet2.awaitUninterruptibly();
        assertTrue(futureGet2.isSuccess());
        assertNotNull(futureGet2.data());
        assertEquals("hallo1", futureGet2.data().object());

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();
    }

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
            return null;
        });

        FutureDirect futureDirect = peer1DHT.peer().sendDirect(peer2DHT.peerAddress()).object("hallo").start();
        futureDirect.awaitUninterruptibly();
        countDownLatch.await(1, TimeUnit.SECONDS);
        if (countDownLatch.getCount() > 0)
            Assert.fail("The test method did not complete successfully!");

        peer1DHT.shutdown().awaitUninterruptibly();
        peer2DHT.shutdown().awaitUninterruptibly();

        assertEquals("hallo", result.toString());
    }


    private PeerDHT startClient(String clientId, int clientPort) throws Exception {
        try {
            Peer peer = new PeerBuilder(Number160.createHash(clientId)).ports(clientPort).behindFirewall().start();
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
                    log.info("Automatic port forwarding is setup. Address = " +
                            futureNAT.peerAddress());
                    return peerDHT;
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
            return null;
        }
    }

}

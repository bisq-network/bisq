package io.bisq.network.p2p.routing;

import io.bisq.network.p2p.DummySeedNode;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import io.bisq.network.p2p.network.LocalhostNetworkNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

// TorNode created. Took 6 sec.
// Hidden service created. Took 40-50 sec.
// Connection establishment takes about 4 sec.

// need to define seed node addresses first before using tor version
//TODO P2P network tests are outdated
@SuppressWarnings({"UnusedAssignment", "EmptyMethod"})
@Ignore
public class PeerManagerTest {
    private static final Logger log = LoggerFactory.getLogger(PeerManagerTest.class);
    private static final int MAX_CONNECTIONS = 100;

    final boolean useLocalhostForP2P = true;
    private CountDownLatch latch;
    private Set<NodeAddress> seedNodes;
    private int sleepTime;
    private DummySeedNode seedNode1, seedNode2, seedNode3;

    @Before
    public void setup() throws InterruptedException {
        LocalhostNetworkNode.setSimulateTorDelayTorNode(50);
        LocalhostNetworkNode.setSimulateTorDelayHiddenService(8);

        seedNodes = new HashSet<>();
        //noinspection ConstantConditions
        if (useLocalhostForP2P) {
            //seedNodes.add(new NodeAddress("localhost:8001"));
            // seedNodes.add(new NodeAddress("localhost:8002"));
            seedNodes.add(new NodeAddress("localhost:8003"));
            sleepTime = 100;

        } else {
            seedNodes.add(new NodeAddress("3omjuxn7z73pxoee.onion:8001"));
            seedNodes.add(new NodeAddress("j24fxqyghjetgpdx.onion:8002"));
            seedNodes.add(new NodeAddress("45367tl6unwec6kw.onion:8003"));
            sleepTime = 1000;
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        Thread.sleep(sleepTime);

        if (seedNode1 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            seedNode1.shutDown(shutDownLatch::countDown);
            shutDownLatch.await();
        }
        if (seedNode2 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            seedNode2.shutDown(shutDownLatch::countDown);
            shutDownLatch.await();
        }
        if (seedNode3 != null) {
            CountDownLatch shutDownLatch = new CountDownLatch(1);
            seedNode3.shutDown(shutDownLatch::countDown);
            shutDownLatch.await();
        }
    }

    // @Test
    public void testSingleSeedNode() throws InterruptedException {
        LocalhostNetworkNode.setSimulateTorDelayTorNode(0);
        LocalhostNetworkNode.setSimulateTorDelayHiddenService(0);
        seedNodes = new HashSet<>();
        NodeAddress nodeAddress = new NodeAddress("localhost:8001");
        seedNodes.add(nodeAddress);
        seedNode1 = new DummySeedNode("test_dummy_dir");
        latch = new CountDownLatch(2);
        seedNode1.createAndStartP2PService(nodeAddress, MAX_CONNECTIONS, useLocalhostForP2P, 2, true,
                seedNodes, new P2PServiceListener() {
                    @Override
                    public void onRequestingDataCompleted() {
                        latch.countDown();
                    }

                    @Override
                    public void onTorNodeReady() {
                    }

                    @Override
                    public void onNoSeedNodeAvailable() {
                    }

                    @Override
                    public void onNoPeersAvailable() {
                    }

                    @Override
                    public void onBootstrapComplete() {
                    }

                    @Override
                    public void onHiddenServicePublished() {
                        latch.countDown();
                    }

                    @Override
                    public void onSetupFailed(Throwable throwable) {

                    }

                    @Override
                    public void onRequestCustomBridges() {

                    }
                });
        P2PService p2PService1 = seedNode1.getSeedNodeP2PService();
        latch.await();
        Thread.sleep(500);
        //Assert.assertEquals(0, p2PService1.getPeerManager().getAuthenticatedAndReportedPeers().size());
    }

    @Test
    public void test2SeedNodes() throws InterruptedException {
        LocalhostNetworkNode.setSimulateTorDelayTorNode(0);
        LocalhostNetworkNode.setSimulateTorDelayHiddenService(0);
        seedNodes = new HashSet<>();
        NodeAddress nodeAddress1 = new NodeAddress("localhost:8001");
        seedNodes.add(nodeAddress1);
        NodeAddress nodeAddress2 = new NodeAddress("localhost:8002");
        seedNodes.add(nodeAddress2);

        latch = new CountDownLatch(6);

        seedNode1 = new DummySeedNode("test_dummy_dir");
        seedNode1.createAndStartP2PService(nodeAddress1, MAX_CONNECTIONS, useLocalhostForP2P, 2, true, seedNodes, new P2PServiceListener() {
            @Override
            public void onRequestingDataCompleted() {
                latch.countDown();
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onBootstrapComplete() {
                latch.countDown();
            }

            @Override
            public void onHiddenServicePublished() {
                latch.countDown();
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onRequestCustomBridges() {

            }
        });
        P2PService p2PService1 = seedNode1.getSeedNodeP2PService();

        Thread.sleep(500);

        seedNode2 = new DummySeedNode("test_dummy_dir");
        seedNode2.createAndStartP2PService(nodeAddress2, MAX_CONNECTIONS, useLocalhostForP2P, 2, true, seedNodes, new P2PServiceListener() {
            @Override
            public void onRequestingDataCompleted() {
                latch.countDown();
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onBootstrapComplete() {
                latch.countDown();
            }

            @Override
            public void onHiddenServicePublished() {
                latch.countDown();
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onRequestCustomBridges() {

            }
        });
        P2PService p2PService2 = seedNode2.getSeedNodeP2PService();
        latch.await();
        // Assert.assertEquals(1, p2PService1.getPeerManager().getAuthenticatedAndReportedPeers().size());
        // Assert.assertEquals(1, p2PService2.getPeerManager().getAuthenticatedAndReportedPeers().size());
    }

    // @Test
    public void testAuthentication() throws InterruptedException {
        log.debug("### start");
        LocalhostNetworkNode.setSimulateTorDelayTorNode(0);
        LocalhostNetworkNode.setSimulateTorDelayHiddenService(0);
        DummySeedNode seedNode1 = getAndStartSeedNode(8001);
        log.debug("### seedNode1");
        Thread.sleep(100);
        log.debug("### seedNode1 100");
        Thread.sleep(1000);
        DummySeedNode seedNode2 = getAndStartSeedNode(8002);

        // authentication:
        // node2 -> node1 RequestAuthenticationMessage
        // node1: close connection
        // node1 -> node2 ChallengeMessage on new connection
        // node2: authentication to node1 done if nonce ok
        // node2 -> node1 GetPeersMessage
        // node1: authentication to node2 done if nonce ok
        // node1 -> node2 PeersMessage

        // first authentication from seedNode2 to seedNode1, then from seedNode1 to seedNode2
        //TODO
       /* CountDownLatch latch1 = new CountDownLatch(2);
        AuthenticationListener routingListener1 = new AuthenticationListener() {
            @Override
            public void onConnectionAuthenticated(Connection connection) {
                log.debug("onConnectionAuthenticated " + connection);
                latch1.countDown();
            }
        };
        seedNode1.getP2PService().getPeerGroup().addPeerListener(routingListener1);

        AuthenticationListener routingListener2 = new AuthenticationListener() {
            @Override
            public void onConnectionAuthenticated(Connection connection) {
                log.debug("onConnectionAuthenticated " + connection);
                latch1.countDown();
            }
        };
        seedNode2.getP2PService().getPeerGroup().addPeerListener(routingListener2);
        latch1.await();
        seedNode1.getP2PService().getPeerGroup().removePeerListener(routingListener1);
        seedNode2.getP2PService().getPeerGroup().removePeerListener(routingListener2);

        // wait until Peers msg finished
        Thread.sleep(sleepTime);

        // authentication:
        // authentication from seedNode3 to seedNode1, then from seedNode1 to seedNode3
        // authentication from seedNode3 to seedNode2, then from seedNode2 to seedNode3
        SeedNode seedNode3 = getAndStartSeedNode(8003);
        CountDownLatch latch2 = new CountDownLatch(3);
        seedNode1.getP2PService().getPeerGroup().addPeerListener(new AuthenticationListener() {
            @Override
            public void onConnectionAuthenticated(Connection connection) {
                log.debug("onConnectionAuthenticated " + connection);
                latch2.countDown();
            }
        });
        seedNode2.getP2PService().getPeerGroup().addPeerListener(new AuthenticationListener() {
            @Override
            public void onConnectionAuthenticated(Connection connection) {
                log.debug("onConnectionAuthenticated " + connection);
                latch2.countDown();
            }
        });
        seedNode3.getP2PService().getPeerGroup().addPeerListener(new AuthenticationListener() {
            @Override
            public void onConnectionAuthenticated(Connection connection) {
                log.debug("onConnectionAuthenticated " + connection);
                latch2.countDown();
            }
        });
        latch2.await();

        // wait until Peers msg finished
        Thread.sleep(sleepTime);


        CountDownLatch shutDownLatch = new CountDownLatch(3);
        seedNode1.shutDown(() -> shutDownLatch.countDown());
        seedNode2.shutDown(() -> shutDownLatch.countDown());
        seedNode3.shutDown(() -> shutDownLatch.countDown());
        shutDownLatch.await();*/
    }

    //@Test
    public void testAuthenticationWithDisconnect() throws InterruptedException {
        //TODO
       /* LocalhostNetworkNode.setSimulateTorDelayTorNode(0);
        LocalhostNetworkNode.setSimulateTorDelayHiddenService(0);
        SeedNode seedNode1 = getAndStartSeedNode(8001);
        SeedNode seedNode2 = getAndStartSeedNode(8002);

        // authentication:
        // node2 -> node1 RequestAuthenticationMessage
        // node1: close connection
        // node1 -> node2 ChallengeMessage on new connection
        // node2: authentication to node1 done if nonce ok
        // node2 -> node1 GetPeersMessage
        // node1: authentication to node2 done if nonce ok
        // node1 -> node2 PeersMessage

        // first authentication from seedNode2 to seedNode1, then from seedNode1 to seedNode2
        CountDownLatch latch1 = new CountDownLatch(2);
        AuthenticationListener routingListener1 = new AuthenticationListener() {
            @Override
            public void onConnectionAuthenticated(Connection connection) {
                log.debug("onConnectionAuthenticated " + connection);
                latch1.countDown();
            }
        };
        seedNode1.getP2PService().getPeerGroup().addPeerListener(routingListener1);

        AuthenticationListener routingListener2 = new AuthenticationListener() {
            @Override
            public void onConnectionAuthenticated(Connection connection) {
                log.debug("onConnectionAuthenticated " + connection);
                latch1.countDown();
            }
        };
        seedNode2.getP2PService().getPeerGroup().addPeerListener(routingListener2);
        latch1.await();

        // shut down node 2
        Thread.sleep(sleepTime);
        seedNode1.getP2PService().getPeerGroup().removePeerListener(routingListener1);
        seedNode2.getP2PService().getPeerGroup().removePeerListener(routingListener2);
        CountDownLatch shutDownLatch1 = new CountDownLatch(1);
        seedNode2.shutDown(() -> shutDownLatch1.countDown());
        shutDownLatch1.await();

        // restart node 2
        seedNode2 = getAndStartSeedNode(8002);
        CountDownLatch latch3 = new CountDownLatch(1);
        routingListener2 = new AuthenticationListener() {
            @Override
            public void onConnectionAuthenticated(Connection connection) {
                log.debug("onConnectionAuthenticated " + connection);
                latch3.countDown();
            }
        };
        seedNode2.getP2PService().getPeerGroup().addPeerListener(routingListener2);
        latch3.await();

        Thread.sleep(sleepTime);

        CountDownLatch shutDownLatch = new CountDownLatch(2);
        seedNode1.shutDown(() -> shutDownLatch.countDown());
        seedNode2.shutDown(() -> shutDownLatch.countDown());
        shutDownLatch.await();*/
    }

    //@Test
    public void testAuthenticationWithManyNodes() throws InterruptedException {
        //TODO
       /* int authentications = 0;
        int length = 3;
        SeedNode[] nodes = new SeedNode[length];
        for (int i = 0; i < length; i++) {
            SeedNode node = getAndStartSeedNode(8001 + i);
            nodes[i] = node;

            latch = new CountDownLatch(i * 2);
            authentications += (i * 2);
            node.getP2PService().getPeerGroup().addPeerListener(new AuthenticationListener() {
                @Override
                public void onConnectionAuthenticated(Connection connection) {
                    log.debug("onConnectionAuthenticated " + connection);
                    latch.countDown();
                }
            });
            latch.await();
            Thread.sleep(sleepTime);
        }

        log.debug("total authentications " + authentications);
        Profiler.printSystemLoad(log);
        // total authentications at 8 nodes = 56
        // total authentications at com nodes = 90, System load (no. threads/used memory (MB)): 170/20
        // total authentications at 20 nodes = 380, System load (no. threads/used memory (MB)): 525/46
        for (int i = 0; i < length; i++) {
            nodes[i].getP2PService().getPeerGroup().printAuthenticatedPeers();
            nodes[i].getP2PService().getPeerGroup().printReportedPeers();
        }

        CountDownLatch shutDownLatch = new CountDownLatch(length);
        for (int i = 0; i < length; i++) {
            nodes[i].shutDown(() -> shutDownLatch.countDown());
        }
        shutDownLatch.await();*/
    }

    private DummySeedNode getAndStartSeedNode(int port) throws InterruptedException {
        DummySeedNode seedNode = new DummySeedNode("test_dummy_dir");

        latch = new CountDownLatch(1);
        seedNode.createAndStartP2PService(new NodeAddress("localhost", port), MAX_CONNECTIONS, useLocalhostForP2P, 2, true, seedNodes, new P2PServiceListener() {
            @Override
            public void onRequestingDataCompleted() {
                latch.countDown();
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onBootstrapComplete() {
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
        });
        latch.await();
        Thread.sleep(sleepTime);
        return seedNode;
    }
}

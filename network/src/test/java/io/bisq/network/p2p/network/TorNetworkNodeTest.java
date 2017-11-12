package io.bisq.network.p2p.network;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bisq.network.p2p.TestUtils;
import io.bisq.network.p2p.mocks.MockPayload;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.concurrent.CountDownLatch;

// TorNode created. Took 6 sec.
// Hidden service created. Took 40-50 sec.
// Connection establishment takes about 4 sec.
//TODO P2P network tests are outdated
@SuppressWarnings("ConstantConditions")
@Ignore
public class TorNetworkNodeTest {
    private static final Logger log = LoggerFactory.getLogger(TorNetworkNodeTest.class);
    private CountDownLatch latch;

    @Before
    public void setup() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testTorNodeBeforeSecondReady() throws InterruptedException, IOException {
        latch = new CountDownLatch(1);
        int port = 9001;
        TorNetworkNode node1 = new TorNetworkNode(port, new File("torNode_" + port), TestUtils.getNetworkProtoResolver(), null);
        node1.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
                log.debug("onReadyForSendingMessages");
            }

            @Override
            public void onHiddenServicePublished() {
                log.debug("onReadyForReceivingMessages");
                latch.countDown();
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onRequestCustomBridges() {

            }
        });
        latch.await();

        latch = new CountDownLatch(1);
        int port2 = 9002;
        TorNetworkNode node2 = new TorNetworkNode(port2, new File("torNode_" + port2), TestUtils.getNetworkProtoResolver(), null);
        node2.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
                log.debug("onReadyForSendingMessages");
                latch.countDown();
            }

            @Override
            public void onHiddenServicePublished() {
                log.debug("onReadyForReceivingMessages");

            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onRequestCustomBridges() {

            }
        });
        latch.await();


        latch = new CountDownLatch(2);
        node1.addMessageListener((message, connection) -> {
            log.debug("onMessage node1 " + message);
            latch.countDown();
        });
        SettableFuture<Connection> future = node2.sendMessage(node1.getNodeAddress(), new MockPayload("msg1"));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.debug("onSuccess ");
                latch.countDown();
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.debug("onFailure ");
            }
        });
        latch.await();


        latch = new CountDownLatch(2);
        node1.shutDown(latch::countDown);
        node2.shutDown(latch::countDown);
        latch.await();
    }

    //@Test
    public void testTorNodeAfterBothReady() throws InterruptedException, IOException {
        latch = new CountDownLatch(2);
        int port = 9001;
        TorNetworkNode node1 = new TorNetworkNode(port, new File("torNode_" + port), TestUtils.getNetworkProtoResolver(), null);
        node1.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
                log.debug("onReadyForSendingMessages");
            }

            @Override
            public void onHiddenServicePublished() {
                log.debug("onReadyForReceivingMessages");
                latch.countDown();
            }

            @Override
            public void onSetupFailed(Throwable throwable) {

            }

            @Override
            public void onRequestCustomBridges() {

            }
        });

        int port2 = 9002;
        TorNetworkNode node2 = new TorNetworkNode(port2, new File("torNode_" + port), TestUtils.getNetworkProtoResolver(), null);
        node2.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
                log.debug("onReadyForSendingMessages");
            }

            @Override
            public void onHiddenServicePublished() {
                log.debug("onReadyForReceivingMessages");
                latch.countDown();
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onRequestCustomBridges() {

            }
        });

        latch.await();

        latch = new CountDownLatch(2);
        node2.addMessageListener((message, connection) -> {
            log.debug("onMessage node2 " + message);
            latch.countDown();
        });
        SettableFuture<Connection> future = node1.sendMessage(node2.getNodeAddress(), new MockPayload("msg1"));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.debug("onSuccess ");
                latch.countDown();
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.debug("onFailure ");
            }
        });
        latch.await();


        latch = new CountDownLatch(2);
        node1.shutDown(latch::countDown);
        node2.shutDown(latch::countDown);
        latch.await();
    }
}

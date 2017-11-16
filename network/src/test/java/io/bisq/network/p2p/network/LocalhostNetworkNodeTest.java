package io.bisq.network.p2p.network;

import io.bisq.network.p2p.TestUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Security;
import java.util.concurrent.CountDownLatch;

// TorNode created. Took 6 sec.
// Hidden service created. Took 40-50 sec.
// Connection establishment takes about 4 sec.

//TODO P2P network tests are outdated
@Ignore
public class LocalhostNetworkNodeTest {
    private static final Logger log = LoggerFactory.getLogger(LocalhostNetworkNodeTest.class);

    @Before
    public void setup() {
        Security.addProvider(new BouncyCastleProvider());
    }


    @Test
    public void testMessage() throws InterruptedException, IOException {
        CountDownLatch msgLatch = new CountDownLatch(2);
        LocalhostNetworkNode node1 = new LocalhostNetworkNode(9001, TestUtils.getNetworkProtoResolver());
        node1.addMessageListener((message, connection) -> {
            log.debug("onMessage node1 " + message);
            msgLatch.countDown();
        });
        CountDownLatch startupLatch = new CountDownLatch(2);
        node1.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
                log.debug("onTorNodeReady");
            }

            @Override
            public void onHiddenServicePublished() {
                log.debug("onHiddenServiceReady");
                startupLatch.countDown();
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                log.debug("onSetupFailed");
            }

            @Override
            public void onRequestCustomBridges() {
            }
        });

        LocalhostNetworkNode node2 = new LocalhostNetworkNode(9002, TestUtils.getNetworkProtoResolver());
        node2.addMessageListener((message, connection) -> {
            log.debug("onMessage node2 " + message);
            msgLatch.countDown();
        });
        node2.start(new SetupListener() {
            @Override
            public void onTorNodeReady() {
                log.debug("onTorNodeReady 2");
            }

            @Override
            public void onHiddenServicePublished() {
                log.debug("onHiddenServiceReady 2");
                startupLatch.countDown();
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                log.debug("onSetupFailed 2");
            }

            @Override
            public void onRequestCustomBridges() {
            }
        });
        startupLatch.await();

        msgLatch.await();

        CountDownLatch shutDownLatch = new CountDownLatch(2);
        node1.shutDown(shutDownLatch::countDown);
        node2.shutDown(shutDownLatch::countDown);
        shutDownLatch.await();
    }
}

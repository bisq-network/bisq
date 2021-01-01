/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.network;

import bisq.network.p2p.TestUtils;

import java.io.IOException;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Ignore;
import org.junit.Test;

// TorNode created. Took 6 sec.
// Hidden service created. Took 40-50 sec.
// Connection establishment takes about 4 sec.

//TODO P2P network tests are outdated
@Ignore
public class LocalhostNetworkNodeTest {
    private static final Logger log = LoggerFactory.getLogger(LocalhostNetworkNodeTest.class);

    @Test
    public void testMessage() throws InterruptedException, IOException {
        CountDownLatch msgLatch = new CountDownLatch(2);
        LocalhostNetworkNode node1 = new LocalhostNetworkNode(9001, TestUtils.getNetworkProtoResolver(), null);
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

        LocalhostNetworkNode node2 = new LocalhostNetworkNode(9002, TestUtils.getNetworkProtoResolver(), null);
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

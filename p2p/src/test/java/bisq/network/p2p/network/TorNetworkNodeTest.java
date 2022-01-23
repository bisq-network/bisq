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
import bisq.network.p2p.mocks.MockPayload;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import org.junit.Ignore;
import org.junit.Test;

// TorNode created. Took 6 sec.
// Hidden service created. Took 40-50 sec.
// Connection establishment takes about 4 sec.
//TODO P2P network tests are outdated
@SuppressWarnings("ConstantConditions")
@Ignore
public class TorNetworkNodeTest {
    private static final Logger log = LoggerFactory.getLogger(TorNetworkNodeTest.class);
    private CountDownLatch latch;


    @Test
    public void testTorNodeBeforeSecondReady() throws InterruptedException, IOException {
        latch = new CountDownLatch(1);
        int port = 9001;
        TorNetworkNode node1 = new TorNetworkNode(port, TestUtils.getNetworkProtoResolver(), false,
                new NewTor(new File("torNode_" + port), null, "", this::getBridgeAddresses), null);
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
        TorNetworkNode node2 = new TorNetworkNode(port2, TestUtils.getNetworkProtoResolver(), false,
                new NewTor(new File("torNode_" + port), null, "", this::getBridgeAddresses), null);
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
        }, MoreExecutors.directExecutor());
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
        TorNetworkNode node1 = new TorNetworkNode(port, TestUtils.getNetworkProtoResolver(), false,
                new NewTor(new File("torNode_" + port), null, "", this::getBridgeAddresses), null);
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
        TorNetworkNode node2 = new TorNetworkNode(port2, TestUtils.getNetworkProtoResolver(), false,
                new NewTor(new File("torNode_" + port), null, "", this::getBridgeAddresses), null);
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
        }, MoreExecutors.directExecutor());
        latch.await();


        latch = new CountDownLatch(2);
        node1.shutDown(latch::countDown);
        node2.shutDown(latch::countDown);
        latch.await();
    }

    public List<String> getBridgeAddresses() {
        return new ArrayList<>();
    }
}

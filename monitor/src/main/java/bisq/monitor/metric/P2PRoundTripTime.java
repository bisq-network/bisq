/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.metric;

import java.io.File;
import java.util.Properties;
import java.util.Random;

import org.jetbrains.annotations.NotNull;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.monitor.Metric;
import bisq.monitor.Reporter;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.NewTor;
import bisq.network.p2p.network.SetupListener;
import bisq.network.p2p.network.TorNetworkNode;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.peers.keepalive.messages.Pong;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class P2PRoundTripTime extends Metric implements MessageListener, SetupListener {

    private NetworkNode networkNode;
    private final File torWorkingDirectory = new File("metric_p2pRoundTripTime");
    private int nonce;
    private long start;
    private Boolean ready = false;

    public P2PRoundTripTime(Reporter reporter) {
        super(reporter);
    }

    @Override
    protected void enable() {
        Thread sepp = new Thread(new Runnable() {
            
            @Override
            public void run() {
                synchronized (ready) {
                    while (!ready)
                        try {
                            ready.wait();
                        } catch (InterruptedException ignore) {
                        }
                    P2PRoundTripTime.super.enable();
                }
            }
        });
        sepp.start();
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        networkNode = new TorNetworkNode(9052, new CoreNetworkProtoResolver(), false,
                new NewTor(torWorkingDirectory, "", "", null));
        Version.setBaseCryptoNetworkId(1); // set to BTC_MAINNET
        networkNode.start(this);
    }

    /**
     * synchronization helper. Required because directly closing the
     * HiddenServiceSocket in its ReadyListener causes a deadlock
     */
    private void await() {
        synchronized (torWorkingDirectory) {
            try {
                torWorkingDirectory.wait();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void proceed() {
        synchronized (torWorkingDirectory) {
            torWorkingDirectory.notify();
        }
    }

    @Override
    protected void execute() {
        nonce = new Random().nextInt();
        start = System.currentTimeMillis();
        SettableFuture<Connection> future = networkNode.sendMessage(new NodeAddress("s67qglwhkgkyvr74.onion:8000"),
                new Ping(nonce, 5));

        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.debug("Send ping to " + connection + " succeeded.");
                connection.addMessageListener(P2PRoundTripTime.this);
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.error("Sending ping failed. That is expected if the peer is offline.\n\tException="
                        + throwable.getMessage());
            }
        });

        await();
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof Pong) {
            Pong pong = (Pong) networkEnvelope;
            if (pong.getRequestNonce() == nonce) {
                reporter.report(System.currentTimeMillis() - start, "bisq." + getName());
            } else {
                log.warn("Nonce not matching. That should never happen.\n\t" +
                                "We drop that message. nonce={} / requestNonce={}",
                        nonce, pong.getRequestNonce());
            }
            connection.removeMessageListener(this);
            proceed();
        }
    }

    @Override
    public void onTorNodeReady() {
        // TODO Auto-generated method stub
    }

    @Override
    public void onHiddenServicePublished() {
        synchronized (ready) {
            ready.notify();
            ready = true;
        }
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onRequestCustomBridges() {
        // TODO Auto-generated method stub

    }
}

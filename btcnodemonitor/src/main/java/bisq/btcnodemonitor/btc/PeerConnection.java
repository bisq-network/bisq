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

package bisq.btcnodemonitor.btc;

import bisq.common.util.SingleThreadExecutorUtils;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.ClientConnectionManager;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.net.SocketAddress;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public class PeerConnection {
    private final Context context;
    private final int disconnectIntervalSec;
    private final int reconnectIntervalSec;
    private final ClientConnectionManager clientConnectionManager;
    private final NetworkParameters params;
    private final int connectTimeoutMillis;
    private final int vMinRequiredProtocolVersion;
    private final PeerConncetionInfo peerConncetionInfo;
    private final AtomicBoolean shutdownCalled = new AtomicBoolean();
    private final ExecutorService connectionExecutor;
    private final ExecutorService onConnectionExecutor;
    private final ExecutorService onDisConnectionExecutor;
    private Optional<PeerConnectedEventListener> peerConnectedEventListener = Optional.empty();
    private Optional<PeerDisconnectedEventListener> peerDisconnectedEventListener = Optional.empty();
    private Optional<CompletableFuture<Void>> connectAndDisconnectFuture = Optional.empty();
    private Optional<CompletableFuture<Void>> innerConnectAndDisconnectFuture = Optional.empty();
    private Optional<CompletableFuture<SocketAddress>> openConnectionFuture = Optional.empty();

    public PeerConnection(Context context,
                          PeerConncetionInfo peerConncetionInfo,
                          BlockingClientManager blockingClientManager,
                          int connectTimeoutMillis,
                          int disconnectIntervalSec,
                          int reconnectIntervalSec) {
        this.context = context;
        this.peerConncetionInfo = peerConncetionInfo;
        this.clientConnectionManager = blockingClientManager;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.disconnectIntervalSec = disconnectIntervalSec;
        this.reconnectIntervalSec = reconnectIntervalSec;

        connectionExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor("connection-" + peerConncetionInfo.getShortId());
        onConnectionExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor("onConnection-" + peerConncetionInfo.getShortId());
        onDisConnectionExecutor = SingleThreadExecutorUtils.getSingleThreadExecutor("onDisConnection-" + peerConncetionInfo.getShortId());

        this.params = context.getParams();
        vMinRequiredProtocolVersion = params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLOOM_FILTER);
    }

    public void start() {
        CompletableFuture.runAsync(() -> {
            do {
                connectAndDisconnectFuture = Optional.of(connectAndDisconnect());
                try {
                    connectAndDisconnectFuture.get().join();
                } catch (Exception ignore) {
                }
            }
            while (!shutdownCalled.get() && !Thread.currentThread().isInterrupted());
            log.info("Exiting startConnectAndDisconnectLoop loop. Expected at shutdown");
        }, connectionExecutor);
    }

    public CompletableFuture<Void> shutdown() {
        log.info("Shutdown");
        shutdownCalled.set(true);
        peerConncetionInfo.getCurrentConnectionAttempt().ifPresent(connectionAttempt -> {
            Peer peer = connectionAttempt.getPeer();
            peerDisconnectedEventListener.ifPresent(peer::removeDisconnectedEventListener);
            peerDisconnectedEventListener = Optional.empty();
            peerConnectedEventListener.ifPresent(peer::removeConnectedEventListener);
            peerConnectedEventListener = Optional.empty();
        });

        return CompletableFuture.runAsync(() -> {
            Context.propagate(context);
            peerConncetionInfo.getCurrentConnectionAttempt()
                    .ifPresent(currentConnectionAttempt -> currentConnectionAttempt.getPeer().close());

            connectAndDisconnectFuture.ifPresent(connectFuture -> connectFuture.complete(null));
            innerConnectAndDisconnectFuture.ifPresent(connectFuture -> connectFuture.complete(null));

            connectionExecutor.shutdownNow();
            onConnectionExecutor.shutdownNow();
            onDisConnectionExecutor.shutdownNow();
        }, SingleThreadExecutorUtils.getSingleThreadExecutor("shutdown-" + peerConncetionInfo.getShortId()));
    }

    private CompletableFuture<Void> connectAndDisconnect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        innerConnectAndDisconnectFuture = Optional.of(CompletableFuture.runAsync(() -> {
            log.info("\n>> Connect to {}", peerConncetionInfo.getAddress());
            Context.propagate(context);
            Peer peer = createPeer(peerConncetionInfo.getPeerAddress());
            PeerConncetionInfo.ConnectionAttempt connectionAttempt = peerConncetionInfo.newConnectionAttempt(peer);
            long ts = System.currentTimeMillis();
            connectionAttempt.setConnectionStartedTs(ts);

            peerConnectedEventListener = Optional.of((p, peerCount) -> {
                peerConnectedEventListener.ifPresent(peer::removeConnectedEventListener);
                peerConnectedEventListener = Optional.empty();
                if (shutdownCalled.get()) {
                    return;
                }
                try {
                    log.info("\n## Successfully connected to {}", peer.getAddress());
                    long now = System.currentTimeMillis();
                    connectionAttempt.setDurationUntilConnection(now - ts);
                    connectionAttempt.setConnectionSuccessTs(now);
                    connectionAttempt.onConnected();

                    try {
                        Thread.sleep(disconnectIntervalSec * 1000L); // 2 sec
                    } catch (InterruptedException ignore) {
                    }
                    if (shutdownCalled.get()) {
                        return;
                    }
                    log.info("Close peer {}", peer.getAddress());
                    peer.close();
                } catch (Exception exception) {
                    log.warn("Exception at onConnection handler. {}", exception.toString());
                    handleException(exception, peer, connectionAttempt, ts, future);
                }
            });
            peer.addConnectedEventListener(onConnectionExecutor, peerConnectedEventListener.get());

            peerDisconnectedEventListener = Optional.of((p, peerCount) -> {
                // At socket timeouts we get called twice from Bitcoinj
                if (peerDisconnectedEventListener.isEmpty()) {
                    log.error("We got called twice at socketimeout from BitcoinJ and ignore the 2nd call.");
                    return;
                }
                peerDisconnectedEventListener.ifPresent(peer::removeDisconnectedEventListener);
                peerDisconnectedEventListener = Optional.empty();
                if (shutdownCalled.get()) {
                    return;
                }
                if (openConnectionFuture.isPresent() && !openConnectionFuture.get().isDone()) {
                    // BitcoinJ calls onDisconnect at sockettimeout without throwing an error on the open connection future.
                    openConnectionFuture.get().completeExceptionally(new TimeoutException("Open connection failed due timeout at PeerSocketHandler"));
                    return;
                }
                try {
                    log.info("\n<< Disconnected from {}", peer.getAddress());
                    long passed = System.currentTimeMillis() - ts;
                    // Timeout is not handled as error in bitcoinj, but it simply disconnects
                    // If we had a successful connect before we got versionMessage set, otherwise its from an error.
                    if (connectionAttempt.getVersionMessage().isEmpty()) {
                        connectionAttempt.setDurationUntilFailure(passed);
                        connectionAttempt.onException(new RuntimeException("Connection failed"));
                    } else {
                        connectionAttempt.setDurationUntilDisConnection(passed);
                        connectionAttempt.onDisconnected();
                    }
                    try {
                        Thread.sleep(reconnectIntervalSec * 2000L); // 120 sec
                    } catch (InterruptedException ignore) {
                    }
                    if (shutdownCalled.get()) {
                        return;
                    }
                    future.complete(null);
                } catch (Exception exception) {
                    log.warn("Exception at onDisconnection handler. {}", exception.toString());
                    handleException(exception, peer, connectionAttempt, ts, future);
                }
            });
            peer.addDisconnectedEventListener(onDisConnectionExecutor, peerDisconnectedEventListener.get());

            try {
                openConnectionFuture = Optional.of(openConnection(peer));
                openConnectionFuture.get().join();
            } catch (Exception exception) {
                log.warn("Error at opening connection to peer {}. {}", peerConncetionInfo, exception.toString());
                handleException(exception, peer, connectionAttempt, ts, future);
            }
        }, MoreExecutors.directExecutor()));

        return future;
    }

    private void handleException(Throwable throwable,
                                 Peer peer,
                                 PeerConncetionInfo.ConnectionAttempt connectionAttempt,
                                 long ts,
                                 CompletableFuture<Void> future) {
        peerDisconnectedEventListener.ifPresent(peer::removeDisconnectedEventListener);
        peerDisconnectedEventListener = Optional.empty();
        peerConnectedEventListener.ifPresent(peer::removeConnectedEventListener);
        peerConnectedEventListener = Optional.empty();
        if (shutdownCalled.get()) {
            return;
        }
        connectionAttempt.setDurationUntilFailure(System.currentTimeMillis() - ts);
        connectionAttempt.onException(throwable);
        try {
            // Try disconnect
            log.info("Try close peer {}", peer.getAddress());
            peer.close();
        } catch (Exception ignore) {
        }

        try {
            Thread.sleep(reconnectIntervalSec * 1000L); // 120 sec
        } catch (InterruptedException ignore) {
        }
        if (shutdownCalled.get()) {
            return;
        }
        future.completeExceptionally(throwable);
    }

    private Peer createPeer(PeerAddress address) {
        Peer peer = new Peer(params, getVersionMessage(address), address, null, 0, 0);
        peer.setMinProtocolVersion(vMinRequiredProtocolVersion);
        peer.setSocketTimeout(connectTimeoutMillis);
        return peer;
    }

    private CompletableFuture<SocketAddress> openConnection(Peer peer) {
        CompletableFuture<SocketAddress> future = new CompletableFuture<>();
        ListenableFuture<SocketAddress> listenableFuture = clientConnectionManager.openConnection(peer.getAddress().toSocketAddress(), peer);
        Futures.addCallback(listenableFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(SocketAddress result) {
                future.complete(result);
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }, MoreExecutors.directExecutor());
        return future;
    }

    private VersionMessage getVersionMessage(PeerAddress address) {
        VersionMessage versionMessage = new VersionMessage(params, 0);
        versionMessage.bestHeight = 0;
        versionMessage.time = Utils.currentTimeSeconds();
        versionMessage.receivingAddr = address;
        versionMessage.receivingAddr.setParent(versionMessage);
        return versionMessage;
    }
}

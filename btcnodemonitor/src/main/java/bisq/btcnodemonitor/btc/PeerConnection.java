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

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.util.SingleThreadExecutorUtils;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.ClientConnectionManager;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.net.SocketAddress;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    private Optional<Timer> disconnectScheduler = Optional.empty();
    private Optional<Timer> reconnectScheduler = Optional.empty();
    private final AtomicBoolean shutdownCalled = new AtomicBoolean();

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

        this.params = context.getParams();
        vMinRequiredProtocolVersion = params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLOOM_FILTER);
    }

    public CompletableFuture<Void> shutdown() {
        shutdownCalled.set(true);
        disconnectScheduler.ifPresent(Timer::stop);
        reconnectScheduler.ifPresent(Timer::stop);
        return CompletableFuture.runAsync(() -> {
            log.info("shutdown {}", peerConncetionInfo);
            Context.propagate(context);
            disconnect();
        }, SingleThreadExecutorUtils.getSingleThreadExecutor("shutdown-" + peerConncetionInfo.getShortId()));
    }

    public void connect() {
        CompletableFuture.runAsync(() -> {
            log.info("connect {}", peerConncetionInfo);
            Context.propagate(context);
            Peer peer = createPeer(peerConncetionInfo.getPeerAddress());
            PeerConncetionInfo.ConnectionAttempt connectionAttempt = peerConncetionInfo.newConnectionAttempt(peer);
            long ts = System.currentTimeMillis();
            connectionAttempt.setConnectionStartedTs(ts);
            try {
                peer.addConnectedEventListener((peer1, peerCount) -> {
                    connectionAttempt.setDurationUntilConnection(System.currentTimeMillis() - ts);
                    connectionAttempt.setConnectionSuccessTs(System.currentTimeMillis());
                    connectionAttempt.onConnected();
                    startAutoDisconnectAndReconnect();
                });
                peer.addDisconnectedEventListener((peer1, peerCount) -> {
                    long passed = System.currentTimeMillis() - ts;
                    // Timeout is not handled as error in bitcoinj, but it simply disconnects
                    // If we had a successful connect before we got versionMessage set, otherwise its from an error.
                    if (connectionAttempt.getVersionMessage().isEmpty()) {
                        connectionAttempt.setDurationUntilFailure(passed);
                        connectionAttempt.onException(new TimeoutException("Connection timeout. Could not connect after " + passed / 1000 + " sec."));
                    } else {
                        connectionAttempt.setDurationUntilDisConnection(passed);
                        connectionAttempt.onDisconnected();
                    }
                    startAutoDisconnectAndReconnect();
                });
                openConnection(peer).join();
            } catch (Exception exception) {
                log.warn("Error at opening connection to peer {}", peerConncetionInfo, exception);
                connectionAttempt.setDurationUntilFailure(System.currentTimeMillis() - ts);
                connectionAttempt.onException(exception);
                startAutoDisconnectAndReconnect();
            }
        }, SingleThreadExecutorUtils.getSingleThreadExecutor("connect-" + peerConncetionInfo.getShortId()));
    }

    private CompletableFuture<Void> disconnect() {
        return peerConncetionInfo.getCurrentConnectionAttempt()
                .map(currentConnectionAttempt -> CompletableFuture.runAsync(() -> {
                            log.info("disconnect {}", peerConncetionInfo);
                            Context.propagate(context);
                            currentConnectionAttempt.getPeer().close();
                        },
                        SingleThreadExecutorUtils.getSingleThreadExecutor("disconnect-" + peerConncetionInfo.getShortId())))
                .orElse(CompletableFuture.completedFuture(null));
    }

    private void startAutoDisconnectAndReconnect() {
        if (shutdownCalled.get()) {
            return;
        }
        disconnectScheduler.ifPresent(Timer::stop);
        disconnectScheduler = Optional.of(UserThread.runAfter(() -> {
            if (shutdownCalled.get()) {
                return;
            }
            disconnect()
                    .thenRun(() -> {
                        if (shutdownCalled.get()) {
                            return;
                        }
                        reconnectScheduler.ifPresent(Timer::stop);
                        reconnectScheduler = Optional.of(UserThread.runAfter(() -> {
                            if (shutdownCalled.get()) {
                                return;
                            }
                            connect();
                        }, reconnectIntervalSec));
                    });
        }, disconnectIntervalSec));
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

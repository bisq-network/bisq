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

package bisq.network.p2p.peers;

import bisq.network.p2p.BundleOfEnvelopes;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.storage.messages.BroadcastMessage;

import bisq.common.Timer;
import bisq.common.UserThread;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public class BroadcastHandler implements PeerManager.Listener {
    private static final long BASE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(120);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    interface ResultHandler {
        void onCompleted(BroadcastHandler broadcastHandler);
    }

    public interface Listener {
        void onSufficientlyBroadcast(List<Broadcaster.BroadcastRequest> broadcastRequests);

        void onNotSufficientlyBroadcast(int numOfCompletedBroadcasts, int numOfFailedBroadcast);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final ResultHandler resultHandler;
    private final String uid;

    private boolean stopped, timeoutTriggered;
    private int numOfCompletedBroadcasts, numOfFailedBroadcasts, numPeersForBroadcast;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    BroadcastHandler(NetworkNode networkNode, PeerManager peerManager, ResultHandler resultHandler) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.resultHandler = resultHandler;
        uid = UUID.randomUUID().toString();

        peerManager.addListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcast(List<Broadcaster.BroadcastRequest> broadcastRequests, boolean shutDownRequested) {
        List<Connection> confirmedConnections = new ArrayList<>(networkNode.getConfirmedConnections());
        Collections.shuffle(confirmedConnections);

        int delay;
        if (shutDownRequested) {
            delay = 1;
            // We sent to all peers as in case we had offers we want that it gets removed with higher reliability
            numPeersForBroadcast = confirmedConnections.size();
        } else {
            if (requestsContainOwnMessage(broadcastRequests)) {
                // The broadcastRequests contains at least 1 message we have originated, so we send to all peers and
                // with shorter delay
                numPeersForBroadcast = confirmedConnections.size();
                delay = 50;
            } else {
                // Relay nodes only send to max 7 peers and with longer delay
                numPeersForBroadcast = Math.min(7, confirmedConnections.size());
                delay = 100;
            }
        }

        setupTimeoutHandler(broadcastRequests, delay, shutDownRequested);

        int iterations = numPeersForBroadcast;
        for (int i = 0; i < iterations; i++) {
            long minDelay = (i + 1) * delay;
            long maxDelay = (i + 2) * delay;
            Connection connection = confirmedConnections.get(i);
            UserThread.runAfterRandomDelay(() -> {
                if (stopped) {
                    return;
                }

                // We use broadcastRequests which have excluded the requests for messages the connection has
                // originated to avoid sending back the message we received. We also remove messages not satisfying
                // capability checks.
                List<Broadcaster.BroadcastRequest> broadcastRequestsForConnection = getBroadcastRequestsForConnection(connection, broadcastRequests);

                // Could be empty list...
                if (broadcastRequestsForConnection.isEmpty()) {
                    // We decrease numPeers in that case for making completion checks correct.
                    if (numPeersForBroadcast > 0) {
                        numPeersForBroadcast--;
                    }
                    checkForCompletion();
                    return;
                }

                if (connection.isStopped()) {
                    // Connection has died in the meantime. We skip it.
                    // We decrease numPeers in that case for making completion checks correct.
                    if (numPeersForBroadcast > 0) {
                        numPeersForBroadcast--;
                    }
                    checkForCompletion();
                    return;
                }

                sendToPeer(connection, broadcastRequestsForConnection);
            }, minDelay, maxDelay, TimeUnit.MILLISECONDS);
        }
    }

    public void cancel() {
        stopped = true;
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        cleanup();
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
    }

    @Override
    public void onAwakeFromStandby() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Check if we have at least one message originated by ourselves
    private boolean requestsContainOwnMessage(List<Broadcaster.BroadcastRequest> broadcastRequests) {
        NodeAddress myAddress = networkNode.getNodeAddress();
        if (myAddress == null)
            return false;

        return broadcastRequests.stream().anyMatch(e -> myAddress.equals(e.getSender()));
    }

    private void setupTimeoutHandler(List<Broadcaster.BroadcastRequest> broadcastRequests,
                                     int delay,
                                     boolean shutDownRequested) {
        // In case of shutdown we try to complete fast and set a short 1 second timeout
        long baseTimeoutMs = shutDownRequested ? TimeUnit.SECONDS.toMillis(1) : BASE_TIMEOUT_MS;
        long timeoutDelay = baseTimeoutMs + delay * (numPeersForBroadcast + 1); // We added 1 in the loop
        timeoutTimer = UserThread.runAfter(() -> {
            if (stopped) {
                return;
            }

            timeoutTriggered = true;

            log.warn("Broadcast did not complete after {} sec.\n" +
                            "numPeersForBroadcast={}\n" +
                            "numOfCompletedBroadcasts={}\n" +
                            "numOfFailedBroadcasts={}",
                    timeoutDelay / 1000d,
                    numPeersForBroadcast,
                    numOfCompletedBroadcasts,
                    numOfFailedBroadcasts);

            maybeNotifyListeners(broadcastRequests);

            cleanup();

        }, timeoutDelay, TimeUnit.MILLISECONDS);
    }

    // We exclude the requests containing a message we received from that connection
    // Also we filter out messages which requires a capability but peer does not support it.
    private List<Broadcaster.BroadcastRequest> getBroadcastRequestsForConnection(Connection connection,
                                                                                 List<Broadcaster.BroadcastRequest> broadcastRequests) {
        return broadcastRequests.stream()
                .filter(broadcastRequest -> !connection.getPeersNodeAddressOptional().isPresent() ||
                        !connection.getPeersNodeAddressOptional().get().equals(broadcastRequest.getSender()))
                .filter(broadcastRequest -> connection.testCapability(broadcastRequest.getMessage()))
                .collect(Collectors.toList());
    }

    private void sendToPeer(Connection connection, List<Broadcaster.BroadcastRequest> broadcastRequestsForConnection) {
        // Can be BundleOfEnvelopes or a single BroadcastMessage
        BroadcastMessage broadcastMessage = getMessage(broadcastRequestsForConnection);
        SettableFuture<Connection> future = networkNode.sendMessage(connection, broadcastMessage);

        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(Connection connection) {
                numOfCompletedBroadcasts++;

                if (stopped) {
                    return;
                }

                maybeNotifyListeners(broadcastRequestsForConnection);
                checkForCompletion();
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.warn("Broadcast to {} failed. ErrorMessage={}", connection.getPeersNodeAddressOptional(),
                        throwable.getMessage());
                numOfFailedBroadcasts++;

                if (stopped) {
                    return;
                }

                maybeNotifyListeners(broadcastRequestsForConnection);
                checkForCompletion();
            }
        }, MoreExecutors.directExecutor());
    }

    private BroadcastMessage getMessage(List<Broadcaster.BroadcastRequest> broadcastRequests) {
        if (broadcastRequests.size() == 1) {
            // If we only have 1 message we avoid the overhead of the BundleOfEnvelopes and send the message directly
            return broadcastRequests.get(0).getMessage();
        } else {
            return new BundleOfEnvelopes(broadcastRequests.stream()
                    .map(Broadcaster.BroadcastRequest::getMessage)
                    .collect(Collectors.toList()));
        }
    }

    private void maybeNotifyListeners(List<Broadcaster.BroadcastRequest> broadcastRequests) {
        int numOfCompletedBroadcastsTarget = Math.max(1, Math.min(numPeersForBroadcast, 3));
        // We use equal checks to avoid duplicated listener calls as it would be the case with >= checks.
        if (numOfCompletedBroadcasts == numOfCompletedBroadcastsTarget) {
            // We have heard back from 3 peers (or all peers if numPeers is lower) so we consider the message was sufficiently broadcast.
            broadcastRequests.stream()
                    .filter(broadcastRequest -> broadcastRequest.getListener() != null)
                    .map(Broadcaster.BroadcastRequest::getListener)
                    .forEach(listener -> listener.onSufficientlyBroadcast(broadcastRequests));
        } else {
            // We check if number of open requests to peers is less than we need to reach numOfCompletedBroadcastsTarget.
            // Thus we never can reach required resilience as too many numOfFailedBroadcasts occurred.
            int maxPossibleSuccessCases = numPeersForBroadcast - numOfFailedBroadcasts;
            // We subtract 1 as we want to have it called only once, with a < comparision we would trigger repeatedly.
            boolean notEnoughSucceededOrOpen = maxPossibleSuccessCases == numOfCompletedBroadcastsTarget - 1;
            // We did not reach resilience level and timeout prevents to reach it later
            boolean timeoutAndNotEnoughSucceeded = timeoutTriggered && numOfCompletedBroadcasts < numOfCompletedBroadcastsTarget;
            if (notEnoughSucceededOrOpen || timeoutAndNotEnoughSucceeded) {
                broadcastRequests.stream()
                        .filter(broadcastRequest -> broadcastRequest.getListener() != null)
                        .map(Broadcaster.BroadcastRequest::getListener)
                        .forEach(listener -> listener.onNotSufficientlyBroadcast(numOfCompletedBroadcasts, numOfFailedBroadcasts));
            }
        }
    }

    private void checkForCompletion() {
        if (numOfCompletedBroadcasts + numOfFailedBroadcasts == numPeersForBroadcast) {
            cleanup();
        }
    }

    private void cleanup() {
        stopped = true;
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
        peerManager.removeListener(this);
        resultHandler.onCompleted(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BroadcastHandler)) return false;

        BroadcastHandler that = (BroadcastHandler) o;

        return uid.equals(that.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }
}

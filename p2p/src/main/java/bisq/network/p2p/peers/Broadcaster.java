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

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.storage.messages.BroadcastMessage;

import bisq.common.Timer;
import bisq.common.UserThread;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

@Slf4j
public class Broadcaster implements BroadcastHandler.ResultHandler {
    private static final long BROADCAST_INTERVAL_MS = 2000;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Set<BroadcastHandler> broadcastHandlers = new CopyOnWriteArraySet<>();
    private final List<BroadcastRequest> broadcastRequests = new ArrayList<>();
    private Timer timer;
    private boolean shutDownRequested;
    private Runnable shutDownResultHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Broadcaster(NetworkNode networkNode, PeerManager peerManager) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
    }

    public void shutDown(Runnable resultHandler) {
        log.info("Broadcaster shutdown started");
        shutDownRequested = true;
        shutDownResultHandler = resultHandler;
        if (broadcastRequests.isEmpty()) {
            doShutDown();
        } else {
            // We set delay of broadcasts and timeout to very low values,
            // so we can expect that we get onCompleted called very fast and trigger the doShutDown from there.
            maybeBroadcastBundle();
        }
    }

    public void flush() {
        maybeBroadcastBundle();
    }

    private void doShutDown() {
        log.info("Broadcaster doShutDown started");
        broadcastHandlers.forEach(BroadcastHandler::cancel);
        if (timer != null) {
            timer.stop();
        }
        shutDownResultHandler.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcast(BroadcastMessage message,
                          @Nullable NodeAddress sender) {
        broadcast(message, sender, null);
    }


    public void broadcast(BroadcastMessage message,
                          @Nullable NodeAddress sender,
                          @Nullable BroadcastHandler.Listener listener) {
        broadcastRequests.add(new BroadcastRequest(message, sender, listener));
        // Keep that log on INFO for better debugging if the feature works as expected. Later it can
        // be remove or set to DEBUG
        log.debug("Broadcast requested for {}. We queue it up for next bundled broadcast.",
                message.getClass().getSimpleName());

        if (timer == null) {
            timer = UserThread.runAfter(this::maybeBroadcastBundle, BROADCAST_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void maybeBroadcastBundle() {
        if (!broadcastRequests.isEmpty()) {
            log.debug("Broadcast bundled requests of {} messages. Message types: {}",
                    broadcastRequests.size(),
                    broadcastRequests.stream().map(e -> e.getMessage().getClass().getSimpleName()).collect(Collectors.toList()));
            BroadcastHandler broadcastHandler = new BroadcastHandler(networkNode, peerManager, this);
            broadcastHandlers.add(broadcastHandler);
            broadcastHandler.broadcast(new ArrayList<>(broadcastRequests), shutDownRequested);
            broadcastRequests.clear();

            if (timer != null) {
                timer.stop();
            }
            timer = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BroadcastHandler.ResultHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCompleted(BroadcastHandler broadcastHandler) {
        broadcastHandlers.remove(broadcastHandler);
        if (shutDownRequested) {
            doShutDown();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BroadcastRequest class
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Value
    public static class BroadcastRequest {
        private BroadcastMessage message;
        @Nullable
        private NodeAddress sender;
        @Nullable
        private BroadcastHandler.Listener listener;

        private BroadcastRequest(BroadcastMessage message,
                                 @Nullable NodeAddress sender,
                                 @Nullable BroadcastHandler.Listener listener) {
            this.message = message;
            this.sender = sender;
            this.listener = listener;
        }
    }
}

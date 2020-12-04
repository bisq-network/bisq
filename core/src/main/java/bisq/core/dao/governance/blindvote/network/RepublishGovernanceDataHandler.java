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

package bisq.core.dao.governance.blindvote.network;

import bisq.core.dao.governance.blindvote.network.messages.RepublishGovernanceDataRequest;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.peerexchange.Peer;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Responsible for sending a RepublishGovernanceDataRequest to full nodes.
 * Processing of RepublishBlindVotesRequests at full nodes is done in the FullNodeNetworkService.
 */
@Slf4j
public final class RepublishGovernanceDataHandler {
    private static final long TIMEOUT = 120;

    private final Collection<NodeAddress> seedNodeAddresses;
    private final NetworkNode networkNode;
    private final PeerManager peerManager;

    private boolean stopped;
    private Timer timeoutTimer;

    @Inject
    public RepublishGovernanceDataHandler(NetworkNode networkNode,
                                          PeerManager peerManager,
                                          SeedNodeRepository seedNodesRepository) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.seedNodeAddresses = new HashSet<>(seedNodesRepository.getSeedNodeAddresses());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendRepublishRequest() {
        // First try if we have a seed node in our connections. All seed nodes are full nodes.
        if (!stopped)
            connectToNextNode();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void sendRepublishRequest(NodeAddress nodeAddress) {
        RepublishGovernanceDataRequest republishGovernanceDataRequest = new RepublishGovernanceDataRequest();
        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {
                        // setup before sending to avoid race conditions
                        if (!stopped) {
                            String errorMessage = "A timeout occurred at sending republishGovernanceDataRequest:" +
                                    " to nodeAddress:" + nodeAddress;
                            log.warn(errorMessage);
                            connectToNextNode();
                        } else {
                            log.warn("We have stopped already. We ignore that timeoutTimer.run call. " +
                                    "Might be caused by a previous networkNode.sendMessage.onFailure.");
                        }
                    },
                    TIMEOUT);
        }

        log.info("We send to peer {} a republishGovernanceDataRequest.", nodeAddress);
        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, republishGovernanceDataRequest);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(Connection connection) {
                if (!stopped) {
                    log.info("Sending of RepublishGovernanceDataRequest message to peer {} succeeded.", nodeAddress.getFullAddress());
                    stop();
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call." +
                            "Might be caused by a previous timeout.");
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                if (!stopped) {
                    String errorMessage = "Sending republishGovernanceDataRequest to " + nodeAddress +
                            " failed. That is expected if the peer is offline.\n\t" +
                            "\n\tException=" + throwable.getMessage();
                    log.info(errorMessage);
                    handleFault(nodeAddress);
                    connectToNextNode();
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call. " +
                            "Might be caused by a previous timeout.");
                }
            }
        }, MoreExecutors.directExecutor());
    }

    private void connectToNextNode() {
        // First we try our connected seed nodes
        Optional<Connection> connectionToSeedNodeOptional = networkNode.getConfirmedConnections().stream()
                .filter(peerManager::isSeedNode)
                .findAny();
        if (connectionToSeedNodeOptional.isPresent() &&
                connectionToSeedNodeOptional.get().getPeersNodeAddressOptional().isPresent()) {
            NodeAddress nodeAddress = connectionToSeedNodeOptional.get().getPeersNodeAddressOptional().get();
            sendRepublishRequest(nodeAddress);
        } else {
            // If connected seed nodes did not confirm receipt of message we try next seed node from seedNodeAddresses
            List<NodeAddress> list = seedNodeAddresses.stream()
                    .filter(e -> peerManager.isSeedNode(e) && !peerManager.isSelf(e))
                    .collect(Collectors.toList());
            Collections.shuffle(list);

            if (!list.isEmpty()) {
                NodeAddress nodeAddress = list.get(0);
                seedNodeAddresses.remove(nodeAddress);
                sendRepublishRequest(nodeAddress);
            } else {
                log.warn("No more seed nodes available. We try any of our other peers.");
                connectToAnyFullNode();
            }
        }
    }

    // TODO support also lite nodes
    private void connectToAnyFullNode() {
        Capabilities required = new Capabilities(Capability.DAO_FULL_NODE);

        List<Peer> list = peerManager.getLivePeers().stream()
                .filter(peer -> peer.getCapabilities().containsAll(required))
                .collect(Collectors.toList());

        if (list.isEmpty())
            list = peerManager.getReportedPeers().stream()
                    .filter(peer -> peer.getCapabilities().containsAll(required))
                    .collect(Collectors.toList());

        if (list.isEmpty())
            list = peerManager.getPersistedPeers().stream()
                    .filter(peer -> peer.getCapabilities().containsAll(required))
                    .collect(Collectors.toList());

        if (!list.isEmpty()) {
            // We avoid the complexity to maintain the results of all our peers and to iterate all until we find a good peer,
            // but we prefer simplicity with the risk that we don't get the data so we request from max 4 peers in parallel
            // assuming that at least one will republish and therefore we should receive all data.
            list = new ArrayList<>(list.subList(0, Math.min(list.size(), 4)));
            list.stream()
                    .map(Peer::getNodeAddress)
                    .forEach(this::sendRepublishRequest);
        } else {
            log.warn("No other nodes found. We try again in 60 seconds.");
            UserThread.runAfter(this::connectToNextNode, 60);
        }
    }

    private void handleFault(NodeAddress nodeAddress) {
        peerManager.handleConnectionFault(nodeAddress);
    }

    private void stop() {
        stopped = true;
        stopTimeoutTimer();
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}

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

package bisq.core.dao.node.lite.network;

import bisq.core.dao.node.full.DaoBlockSignatureHash;
import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.lite.DaoBlockSignatureVerifier;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.NewBlockBroadcastMessage;
import bisq.core.dao.node.messages.SignedRawBlock;
import bisq.core.dao.state.DaoStateService;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.Utilities;

import com.google.common.annotations.VisibleForTesting;

import javax.inject.Inject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

/**
 * Responsible for requesting BSQ blocks from a full node and for listening to new blocks broadcasted by full nodes.
 */
@Slf4j
public class LiteNodeNetworkService implements MessageListener, ConnectionListener, PeerManager.Listener {

    private static final long RETRY_DELAY_SEC = 10;
    private static final int MAX_RETRY = 12;
    // We use and relay the announcement of a block which is less than that many blocks behind our chain height.
    // About a week of blocks at the usual Bitcoin block interval.
    static final int MAX_RECENT_BLOCKS = 1024;

    private int retryCounter = 0;
    // Start height of the last block request until it is answered. 0 if there is no such request.
    private int lastRequestedBlockHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onNoSeedNodeAvailable();

        void onRequestedBlocksReceived(GetBlocksResponse getBlocksResponse,
                                       NodeAddress senderNodeAddress,
                                       Runnable onParsingComplete);

        void onNewBlockReceived(NewBlockBroadcastMessage newBlockBroadcastMessage,
                                @Nullable NodeAddress senderNodeAddress);

        void onFault(String errorMessage, @Nullable Connection connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Broadcaster broadcaster;
    private final Collection<NodeAddress> seedNodeAddresses;
    private final DaoBlockSignatureVerifier daoBlockSignatureVerifier;
    private final DaoStateService daoStateService;

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    // At most one block request runs. A request for another start height replaces it, and the retries and the tried
    // seed nodes count per request, so that earlier requests cannot use them up.
    @Nullable
    private RequestBlocksHandler requestBlocksHandler;
    private final Set<NodeAddress> triedSeedNodeAddresses = new HashSet<>();
    private Timer retryTimer;
    private boolean stopped;
    // The accepted announcements by block height. We forget an announcement only when its height has left the
    // window of MAX_RECENT_BLOCKS, so we know every announcement which we would still accept.
    private final TreeMap<Integer, Set<String>> receivedBlockUidsByHeight = new TreeMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public LiteNodeNetworkService(NetworkNode networkNode,
                                  PeerManager peerManager,
                                  Broadcaster broadcaster,
                                  SeedNodeRepository seedNodesRepository,
                                  DaoBlockSignatureVerifier daoBlockSignatureVerifier,
                                  DaoStateService daoStateService) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.broadcaster = broadcaster;
        // seedNodeAddresses can be empty (in case there is only 1 seed node, the seed node starting up has no other seed nodes)
        this.seedNodeAddresses = new HashSet<>(seedNodesRepository.getSeedNodeAddresses());
        this.daoBlockSignatureVerifier = daoBlockSignatureVerifier;
        this.daoStateService = daoStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);
        peerManager.addListener(this);
    }

    public void shutDown() {
        stopped = true;
        stopRetryTimer();
        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);
        peerManager.removeListener(this);
        closeHandler();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Starts a new block request with no tried seed nodes and no retries, which replaces the running request. A running
     * request with the same start height asks for the same blocks already. We keep it with its attempt in flight or its
     * waiting retry, as replacing it would drop an attempt which may be answered soon, or wait for the retry again.
     *
     * @param startBlockHeight      Block height from where we expect new blocks (current block height in bsqState + 1)
     */
    public void requestBlocks(int startBlockHeight) {
        if (startBlockHeight == lastRequestedBlockHeight && (requestBlocksHandler != null || retryTimer != null)) {
            log.info("We have a block request with startBlockHeight={} running already.", startBlockHeight);
            return;
        }

        lastRequestedBlockHeight = startBlockHeight;
        stopRequest();
        Optional<Connection> connectionToSeedNodeOptional = networkNode.getConfirmedConnections().stream()
                .filter(peerManager::isSeedNode)
                .findAny();

        connectionToSeedNodeOptional.flatMap(Connection::getPeersNodeAddressOptional)
                .ifPresentOrElse(candidate -> {
                    requestBlocks(candidate, startBlockHeight);
                }, () -> {
                    tryWithNewSeedNode(startBlockHeight);
                });
    }

    @VisibleForTesting
    int getNumReceivedBlocks() {
        return receivedBlockUidsByHeight.values().stream().mapToInt(Set::size).sum();
    }

    public void reset() {
        receivedBlockUidsByHeight.clear();
        lastRequestedBlockHeight = 0;
        stopRequest();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        closeHandler(connection);

        if (peerManager.isPeerBanned(closeConnectionReason, connection)) {
            connection.getPeersNodeAddressOptional().ifPresent(seedNodeAddresses::remove);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        log.info("onAllConnectionsLost");
        closeHandler();
        stopRetryTimer();
        stopped = true;
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        log.info("onNewConnectionAfterAllConnectionsLost");
        stopped = false;
        restartUnansweredRequest();
    }

    @Override
    public void onAwakeFromStandby() {
        log.info("onAwakeFromStandby");
        stopped = false;
        restartUnansweredRequest();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof NewBlockBroadcastMessage newBlockBroadcastMessage) {
            RawBlock unSignedRawBlock = newBlockBroadcastMessage.getBlock();
            SignedRawBlock signedRawBlock = newBlockBroadcastMessage.getSignedRawBlock();
            NodeAddress senderNodeAddress = connection.getPeersNodeAddressOptional().orElse(null);
            if (signedRawBlock == null) {
                log.warn("We received a NewBlockBroadcastMessage without a signed block. We neither use nor relay it. " +
                        "senderNodeAddress={}, blockHeight={}", senderNodeAddress, unSignedRawBlock.getHeight());
                return;
            }

            // Once we have forgotten an announcement, old signed blocks must not circulate again.
            // Missing historical blocks are served by GetBlocksResponse.
            int chainHeight = daoStateService.getChainHeight();
            int blockHeight = signedRawBlock.getBlock().getHeight();
            if ((long) chainHeight - blockHeight >= MAX_RECENT_BLOCKS) {
                log.debug("We ignore a NewBlockBroadcastMessage for a block which is {} or more blocks behind our " +
                                "chain height. senderNodeAddress={}, blockHeight={}, chainHeight={}",
                        MAX_RECENT_BLOCKS, senderNodeAddress, blockHeight, chainHeight);
                return;
            }

            // We relay the message as received, including its unsigned copy of the block. Releases before 1.10.4 use
            // that copy without any check, so we relay only a copy that matches the signed block.
            // Compatibility with v1.10.11 and earlier: these releases need the copy, and we require it as well. To let
            // full nodes leave it out later, check it here only if the message carries one.
            String contentHashUnsignedBlock = Utilities.bytesAsHexString(DaoBlockSignatureHash.getHash(unSignedRawBlock));
            String blockUid = Utilities.bytesAsHexString(DaoBlockSignatureHash.getHash(signedRawBlock.getBlock()));
            if (!signedRawBlock.getBlock().getHash().equals(unSignedRawBlock.getHash()) ||
                    !blockUid.equals(contentHashUnsignedBlock)) {
                log.warn("We received a newBlockBroadcastMessage with a different hashes for the unsigned and the " +
                        "signed block. We ignore that as invalid. newBlockBroadcastMessage={}", newBlockBroadcastMessage);
                return;
            }

            if (receivedBlockUidsByHeight.getOrDefault(blockHeight, Set.of()).contains(blockUid)) {
                log.debug("We had that message already and do not further broadcast it. blockUid={}", blockUid);
                return;
            }

            // Skipping signature verification is handled by daoBlockSignatureVerifier.isValid.
            if (!daoBlockSignatureVerifier.isValid(signedRawBlock)) {
                log.warn("We received a newBlockBroadcastMessage with an invalid signature. " +
                        "newBlockBroadcastMessage={}", newBlockBroadcastMessage);
                return;
            }

            log.info("We received a NewBlockBroadcastMessage from peer {}. blockUid={}", senderNodeAddress, blockUid);
            receivedBlockUidsByHeight.headMap(chainHeight - MAX_RECENT_BLOCKS, true).clear();
            receivedBlockUidsByHeight.computeIfAbsent(blockHeight, height -> new HashSet<>()).add(blockUid);
            broadcaster.broadcast(newBlockBroadcastMessage, senderNodeAddress);
            listeners.forEach(listener -> listener.onNewBlockReceived(newBlockBroadcastMessage, senderNodeAddress));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RequestData
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestBlocks(NodeAddress peersNodeAddress, int startBlockHeight) {
        if (stopped) {
            log.warn("We have stopped already. We ignore that requestData call.");
            return;
        }

        triedSeedNodeAddresses.add(peersNodeAddress);
        RequestBlocksHandler handler = new RequestBlocksHandler(networkNode,
                peerManager,
                peersNodeAddress,
                startBlockHeight,
                new RequestBlocksHandler.Listener() {
                    @Override
                    public void onComplete(GetBlocksResponse getBlocksResponse) {
                        log.info("requestBlocksHandler to {} completed", peersNodeAddress);
                        stopRetryTimer();

                        // need to remove before listeners are notified as they cause the update call
                        requestBlocksHandler = null;
                        lastRequestedBlockHeight = 0;
                        listeners.forEach(listener -> listener.onRequestedBlocksReceived(getBlocksResponse,
                                peersNodeAddress,
                                () -> {
                                }));
                    }

                    @Override
                    public void onFault(String errorMessage, @Nullable Connection connection) {
                        log.warn("requestBlocksHandler with outbound connection failed.\n\tnodeAddress={}\n\t" +
                                "ErrorMessage={}", peersNodeAddress, errorMessage);

                        peerManager.handleConnectionFault(peersNodeAddress);
                        requestBlocksHandler = null;

                        listeners.forEach(listener -> listener.onFault(errorMessage, connection));

                        tryWithNewSeedNode(startBlockHeight);
                    }
                });
        // Set before sending: if the send future is already done, its callback runs within requestBlocks.
        requestBlocksHandler = handler;
        handler.requestBlocks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void tryWithNewSeedNode(int startBlockHeight) {
        if (networkNode.getAllConnections().isEmpty()) {
            return;
        }

        if (lastRequestedBlockHeight == 0) {
            return;
        }

        if (stopped) {
            return;
        }

        if (retryTimer != null) {
            log.warn("We have a retry timer already running.");
            return;
        }

        retryCounter++;

        if (retryCounter > MAX_RETRY) {
            log.warn("We tried {} times but could not connect to a seed node.", retryCounter);
            listeners.forEach(Listener::onNoSeedNodeAvailable);
            return;
        }

        retryTimer = UserThread.runAfter(() -> {
                    stopped = false;

                    stopRetryTimer();

                    List<NodeAddress> list = seedNodeAddresses.stream()
                            .filter(e -> !triedSeedNodeAddresses.contains(e))
                            .filter(e -> peerManager.isSeedNode(e) && !peerManager.isSelf(e))
                            .collect(Collectors.toList());
                    Collections.shuffle(list);

                    if (!list.isEmpty()) {
                        NodeAddress nextCandidate = list.get(0);
                        log.info("We try requestBlocks from {} with startBlockHeight={}", nextCandidate, startBlockHeight);
                        requestBlocks(nextCandidate, startBlockHeight);
                    } else {
                        log.warn("No more seed nodes available we could try.");
                        listeners.forEach(Listener::onNoSeedNodeAvailable);
                    }
                },
                RETRY_DELAY_SEC);
    }

    private void stopRetryTimer() {
        if (retryTimer != null) {
            retryTimer.stop();
            retryTimer = null;
        }
    }

    // Stops the running block request and clears its retries and tried seed nodes. A late answer or failure of it is
    // ignored.
    private void stopRequest() {
        stopRetryTimer();
        closeHandler();
        retryCounter = 0;
        triedSeedNodeAddresses.clear();
    }

    // The attempts of an unanswered request may have failed because we had lost our connections or were in standby,
    // which tells nothing about the seed nodes. So we request its blocks again with a new request. An answered request
    // is not repeated: the wallet triggers a new request for any block that is still missing.
    private void restartUnansweredRequest() {
        stopRequest();
        tryWithNewSeedNode(lastRequestedBlockHeight);
    }

    private void closeHandler(Connection connection) {
        Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
        if (peersNodeAddressOptional.isPresent()) {
            NodeAddress nodeAddress = peersNodeAddressOptional.get();
            if (requestBlocksHandler != null && requestBlocksHandler.getNodeAddress().equals(nodeAddress)) {
                closeHandler();
            }
        } else {
            log.trace("closeHandler: nodeAddress not set in connection {}", connection);
        }
    }

    private void closeHandler() {
        if (requestBlocksHandler != null) {
            requestBlocksHandler.terminate();
            requestBlocksHandler = null;
        }
    }
}

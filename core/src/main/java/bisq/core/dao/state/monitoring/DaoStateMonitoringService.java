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

package bisq.core.dao.state.monitoring;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.monitoring.messages.GetDaoStateHashRequest;
import bisq.core.dao.state.monitoring.messages.GetDaoStateHashResponse;
import bisq.core.dao.state.monitoring.messages.NewDaoStateHashMessage;
import bisq.core.dao.state.monitoring.network.RequestDaoStateHashHandler;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.UserThread;
import bisq.common.crypto.Hash;
import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Monitors the DaoState with using a hash fo the complete daoState and make it accessible to the network for
 * so we can detect quickly if any consensus issue arise. The data does not contain any private user
 * data so sharing it on demand has no privacy concerns.
 *
 * We request the state from the connected seed nodes after batch processing of BSQ is complete as well as we start
 * to listen for broadcast messages from our peers about dao state of new blocks. It could be that the received dao
 * state from the peers is already covering the next block we have not received yet. So we only take data in account
 * which are inside the block height we have already. To avoid such race conditions we delay the broadcasting of our
 * state to the peers to not get ignored it in case they have not received the block yet.
 *
 * We do not persist that chain of hashes and we only create it from the blocks we parse, so we start from the height
 * of the latest block in the snapshot.
 *
 * TODO maybe request full state?
 * TODO add p2p network data for monitoring
 * TODO auto recovery
 */
@Slf4j
public class DaoStateMonitoringService implements DaoStateListener, DaoSetupService, MessageListener {
    public interface DaoStateMonitorListener {
        void onChange();
    }

    private final DaoStateService daoStateService;
    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Broadcaster broadcaster;
    private final GenesisTxInfo genesisTxInfo;

    // Our chain of dao state changes
    private final LinkedList<DaoStateHash> daoStateHashChain = new LinkedList<>();

    // Lookup map for list of peersDaoStateHashes by block height
    private final Map<Integer, List<PeersDaoStateHash>> peersDaoStateHashMap = new HashMap<>();

    // List of network data state objects. Sorted by blockHeight descending
    @Getter
    private final List<NetworkDaoState> networkDaoStateList = new ArrayList<>();

    private final Map<NodeAddress, RequestDaoStateHashHandler> requestDaoStateHashHandlerMap = new HashMap<>();
    private final List<DaoStateMonitorListener> daoStateMonitorListeners = new CopyOnWriteArrayList<>();
    private boolean parseBlockChainComplete;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateMonitoringService(DaoStateService daoStateService,
                                     NetworkNode networkNode,
                                     PeerManager peerManager,
                                     Broadcaster broadcaster,
                                     GenesisTxInfo genesisTxInfo) {
        this.daoStateService = daoStateService;
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.broadcaster = broadcaster;
        this.genesisTxInfo = genesisTxInfo;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        this.daoStateService.addDaoStateListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockChainComplete() {
        parseBlockChainComplete = true;

        // We wait for processing messages until we have completed batch processing
        networkNode.addMessageListener(this);
        requestHashesFromAllConnectedSeedNodes();
    }

    @Override
    public void onDaoStateChanged(Block block) {
        handleDaoStateChanged(block);

        if (daoStateHashChain.isEmpty()) {
            daoStateService.getBlocks().stream().filter(b -> b.getHeight() != block.getHeight()).forEach(this::handleDaoStateChanged);
        }
    }

    @Override
    public void onSnapshotApplied() {
        // We could got a reset from a reorg, so we clear all and start over from the genesis block.
        daoStateHashChain.clear();
        peersDaoStateHashMap.clear();
        networkDaoStateList.clear();
        requestDaoStateHashHandlerMap.clear();

        daoStateService.getBlocks().forEach(this::handleDaoStateChanged);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (parseBlockChainComplete && networkEnvelope instanceof NewDaoStateHashMessage) {
            NewDaoStateHashMessage newDaoStateHashMessage = (NewDaoStateHashMessage) networkEnvelope;
            log.info("We received a NewDaoStateHashMessage {} from peer {}",
                    newDaoStateHashMessage, connection.getPeersNodeAddressOptional());

            // We ignore dao state data in case we receive them before we have received the block.
            if (newDaoStateHashMessage.getDaoStateHash().getBlockHeight() <= daoStateService.getChainHeight()) {
                addToDaoStateHashesFromNetwork(newDaoStateHashMessage.getDaoStateHash(), connection.getPeersNodeAddressOptional());
            }
        } else if (networkEnvelope instanceof GetDaoStateHashRequest) {
            GetDaoStateHashRequest getDaoStateHashRequest = (GetDaoStateHashRequest) networkEnvelope;
            List<DaoStateHash> daoStateHashes = new ArrayList<>(daoStateHashChain);
            daoStateHashes.sort(Comparator.comparingInt(DaoStateHash::getBlockHeight));

            // We send only last 10 items
            List<DaoStateHash> subList = daoStateHashes.subList(daoStateHashes.size() - 10, daoStateHashes.size());
            GetDaoStateHashResponse response = new GetDaoStateHashResponse(subList, getDaoStateHashRequest.getNonce());
            connection.sendMessage(response);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isDaoStateOutOfSyncWithNetworkAndCritical() {
        if (networkDaoStateList.stream().allMatch(e -> e.getMisMatchList().isEmpty())) {
            return false;
        }

        List<NetworkDaoState> outOfSyncWithNetworkItems = getOutOfSyncWithNetworkItems();
        // should be covered by isDaoStateOutOfSyncWithNetwork anyway
        if (outOfSyncWithNetworkItems.isEmpty()) {
            return false;
        }

        ArrayList<NetworkDaoState> list = new ArrayList<>(outOfSyncWithNetworkItems);
        Comparator<NetworkDaoState> comparator = Comparator.comparing(e -> e.getMisMatchList().size());
        comparator.reversed();
        list.sort(comparator);
        NetworkDaoState itemWithHighestNumMismatch = list.get(0);
        Optional<NetworkDaoState> item = networkDaoStateList.stream()
                .filter(e -> e.getHeight() == itemWithHighestNumMismatch.getHeight())
                .findAny();
        if (item.isPresent()) {
            // If the number of reported diff. state is more then 10% of the total received states we consider it as
            // critical. We want to avoid warnings to users in case there are some single nodes out of sync.
            // If we have less then 4 messages we don't check for the mismatch ratio.
            int messages = item.get().getNumNetworkMessages();
            double mismatchRation = (double) itemWithHighestNumMismatch.getMisMatchList().size() / (double) messages;
            boolean result = messages > 4 && mismatchRation > 0.1;
            if (result)
                return true;
        }

        // We consider it a critical condition if the block out of sync is older than 4 blocks. Typical reorgs in Bitcoin
        // are usually shallow.
        NetworkDaoState lastItem = outOfSyncWithNetworkItems.get(outOfSyncWithNetworkItems.size() - 1);
        int depth = daoStateService.getChainHeight() - lastItem.getHeight();
        return depth >= 4;
    }

    public List<NetworkDaoState> getOutOfSyncWithNetworkItems() {
        return networkDaoStateList.stream().filter(e -> !e.getMisMatchList().isEmpty()).collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addDaoStateListener(DaoStateMonitorListener listener) {
        daoStateMonitorListeners.add(listener);
    }

    public void removeDaoStateListener(DaoStateMonitorListener listener) {
        daoStateMonitorListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleDaoStateChanged(Block block) {
        //TODO handle reorgs TODO need to start from gen

        byte[] prevHash;
        int height = block.getHeight();
        if (daoStateHashChain.isEmpty()) {
            // Only at genesis we allow an empty prevHash
            checkArgument(height == genesisTxInfo.getGenesisBlockHeight());
            prevHash = new byte[0];
        } else {
            prevHash = daoStateHashChain.getLast().getHash();
            if (daoStateHashChain.getLast().getBlockHeight() == height) {
                // In case of a reorg we might receive the same block again.
                //TODO handle
                return;
            }
        }
        byte[] stateHash = daoStateService.getDaoStateHash();
        // We include the prev. hash in our new hash so we can be sure that if one hash is matching all the past would
        // match as well.
        byte[] combined = ArrayUtils.addAll(prevHash, stateHash);
        byte[] hash = Hash.getRipemd160hash(combined);
        DaoStateHash daoStateHash = new DaoStateHash(height, hash, prevHash);
        daoStateHashChain.add(daoStateHash);

        log.info("Add daoStateHash after block parsing:\n{}", daoStateHash);

        // We don't broadcast when batch processing
        if (parseBlockChainComplete) {
            // We delay broadcast to give peers enough time to have received the block.
            // Otherwise they would ignore our message.
            int delayInSec = 1 + new Random().nextInt(5);
            UserThread.runAfter(() -> {
                broadcaster.broadcast(new NewDaoStateHashMessage(daoStateHash),
                        networkNode.getNodeAddress(),
                        null,
                        true);
            }, delayInSec);
        }
    }

    private void requestHashesFromAllConnectedSeedNodes() {
        networkNode.getConfirmedConnections().stream()
                .filter(peerManager::isSeedNode)
                .forEach(connection -> connection.getPeersNodeAddressOptional()
                        .ifPresent(this::requestHashFromSeedNode));
    }

    private void requestHashFromSeedNode(NodeAddress nodeAddress) {
        RequestDaoStateHashHandler requestDaoStateHashHandler = new RequestDaoStateHashHandler(networkNode,
                peerManager,
                nodeAddress,
                new RequestDaoStateHashHandler.Listener() {
                    @Override
                    public void onComplete(GetDaoStateHashResponse getDaoStateHashResponse, Optional<NodeAddress> peersNodeAddress) {
                        log.debug("requestDaoStateHashHandler of outbound connection complete. nodeAddress={}", nodeAddress);
                        requestDaoStateHashHandlerMap.remove(nodeAddress);
                        getDaoStateHashResponse.getDaoStateHashes()
                                .forEach(daoStateHash -> addToDaoStateHashesFromNetwork(daoStateHash, peersNodeAddress));
                    }

                    @Override
                    public void onFault(String errorMessage, @Nullable Connection connection) {
                        log.warn("requestDaoStateHashHandler with outbound connection failed.\n\tnodeAddress={}\n\t" +
                                "ErrorMessage={}", nodeAddress, errorMessage);
                        requestDaoStateHashHandlerMap.remove(nodeAddress);
                    }
                });
        requestDaoStateHashHandlerMap.put(nodeAddress, requestDaoStateHashHandler);
        requestDaoStateHashHandler.requestDaoStateHash();
    }

    private void addToDaoStateHashesFromNetwork(DaoStateHash daoStateHash, Optional<NodeAddress> peersNodeAddress) {
        int height = daoStateHash.getBlockHeight();
        String peersNodeAddressAsString = peersNodeAddress.map(NodeAddress::getFullAddress)
                .orElseGet(() -> "unknown_peer_" + new Random().nextInt(10000));
        peersDaoStateHashMap.putIfAbsent(height, new ArrayList<>());
        PeersDaoStateHash peersDaoStateHash = new PeersDaoStateHash(daoStateHash, peersNodeAddressAsString);
        List<PeersDaoStateHash> peersDaoStateHashes = peersDaoStateHashMap.get(height);

        // Added already so we return
        if (peersDaoStateHashes.stream().anyMatch(e -> e.getPeersNodeAddress().equals(peersNodeAddressAsString))) {
            return;
        }

        peersDaoStateHashes.add(peersDaoStateHash);

        daoStateHashChain.stream().filter(e -> e.getBlockHeight() == height).findAny()
                .ifPresent(myDaoStateHash -> {
                    NetworkDaoState newNetworkDaoState;
                    List<PeersDaoStateHash> misMatchList = new ArrayList<>();
                    Optional<NetworkDaoState> optionalNetworkDaoState = networkDaoStateList.stream().filter(e -> e.getHeight() == height).findAny();
                    if (optionalNetworkDaoState.isPresent()) {
                        NetworkDaoState networkDaoState = optionalNetworkDaoState.get();
                        misMatchList = networkDaoState.getMisMatchList();
                        if (!Arrays.equals(peersDaoStateHash.getDaoStateHash().getHash(), myDaoStateHash.getHash())) {
                            log.warn("We got DaoStateHash from a peer which does not match our own one. It is " +
                                    "recommended to rebuild the DAO state and if it is not recovered afterwards " +
                                    "to contact the developers.\n" +
                                    "myDaoStateHash={}\npeersDaoStateHash={}", myDaoStateHash, peersDaoStateHash);

                            //TODO add autom. requests for p2p network data and trigger a resync in case its a seed node
                            misMatchList.add(peersDaoStateHash);
                        }
                        // We remove old one (and add later new one)
                        networkDaoStateList.remove(networkDaoState);
                    }

                    newNetworkDaoState = new NetworkDaoState(height, misMatchList, peersDaoStateHashes.size(), myDaoStateHash.getHash());
                    networkDaoStateList.add(newNetworkDaoState);
                    networkDaoStateList.sort(Comparator.comparing(NetworkDaoState::getHeight).reversed());
                    daoStateMonitorListeners.forEach(DaoStateMonitorListener::onChange);
                });
    }
}

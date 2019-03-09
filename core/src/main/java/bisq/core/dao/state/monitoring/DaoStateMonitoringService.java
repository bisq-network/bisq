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
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.monitoring.messages.NewDaoStateHashMessage;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.BroadcastHandler;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.storage.messages.BroadcastMessage;

import bisq.common.crypto.Hash;
import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates the DaoState with using a hash fo the complete daoState and make it accessible to the network for
 * monitoring purpose so we can detect quickly if any consensus issue arise. The data does not contain any private user
 * data so sharing it on demand has no privacy concerns.
 *
 * //todo broadcast msg with blockHeight, daoStateHashChainHash and daoStateHashList
 * listen to such msg and compare state, if not matching report to listeners
 * // maybe request full state?
 *
 *  add p2p network data for monitoring
 */
@Slf4j
public class DaoStateMonitoringService implements DaoStateListener, DaoSetupService, MessageListener {
    private final DaoStateService daoStateService;
    private final NetworkNode networkNode;
    private final Broadcaster broadcaster;
    private final LinkedList<DaoStateHash> daoStateHashChain = new LinkedList<>();
    private final Map<Integer, List<DaoStateHash>> daoStateHashesFromNetwork = new HashMap<>();

    // key is blockHeight, value is ratio of matching
    @Getter
    private final Map<Integer, DaoStateNetworkConsensus> daoStateNetworkConsensusMap = new HashMap<>();
    private final List<DaoStateMonitorListener> daoStateMonitorListeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateMonitoringService(DaoStateService daoStateService,
                                     NetworkNode networkNode,
                                     Broadcaster broadcaster) {
        this.daoStateService = daoStateService;
        this.networkNode = networkNode;
        this.broadcaster = broadcaster;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        this.daoStateService.addDaoStateListener(this);
        networkNode.addMessageListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDaoStateChanged(Block block) {
        //TODO handle reorgs

        // We don't want to broadcast when batch processing
        if (daoStateService.isParseBlockChainComplete()) {

            byte[] prevHash = !daoStateHashChain.isEmpty() ? daoStateHashChain.getLast().getHash() : new byte[0];
            DaoStateHash daoStateHash = new DaoStateHash(block.getHeight(),
                    Hash.getSha256Ripemd160hash(daoStateService.getDaoStateHash()),
                    prevHash);
            daoStateHashChain.add(daoStateHash);

            broadcaster.broadcast(new NewDaoStateHashMessage(daoStateHash),
                    networkNode.getNodeAddress(),
                    new BroadcastHandler.Listener() {
                        @Override
                        public void onBroadcasted(BroadcastMessage message, int numOfCompletedBroadcasts) {
                        }

                        @Override
                        public void onBroadcastedToFirstPeer(BroadcastMessage message) {
                        }

                        @Override
                        public void onBroadcastCompleted(BroadcastMessage message, int numOfCompletedBroadcasts, int numOfFailedBroadcasts) {
                            daoStateNetworkConsensusChanged();
                        }

                        @Override
                        public void onBroadcastFailed(String errorMessage) {
                        }
                    },
                    true);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof NewDaoStateHashMessage) {
            log.info("We received a NewDaoStateHashMessage from peer {}", connection.getPeersNodeAddressOptional());

            NewDaoStateHashMessage newDaoStateHashMessage = (NewDaoStateHashMessage) networkEnvelope;
            int blockHeight = newDaoStateHashMessage.getDaoStateHash().getBlockHeight();
            daoStateHashesFromNetwork.putIfAbsent(blockHeight, new ArrayList<>());
            // TODO can we receive multiple msg from same peer? I think not.
            daoStateHashesFromNetwork.get(blockHeight).add(newDaoStateHashMessage.getDaoStateHash());

            daoStateNetworkConsensusChanged();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getDaoStateNetworkConsensusInfo() {
        StringBuilder sb = new StringBuilder();
        daoStateNetworkConsensusMap.forEach((key, value) -> {
            sb.append("Block height: ").append(key)
                    .append(" / Received dao states from peers: ").append(value.getNumNetworkMessages())
                    .append(" / Peers with different dao state: ").append(value.getMisMatch().size())
                    .append("\n");
        });
        return sb.toString();
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

    private void daoStateNetworkConsensusChanged() {
        daoStateHashesFromNetwork.forEach((key, list) -> {
            int height = key;
            getMyDaoStateHashByHeight(height).ifPresent(myDaoStateHash -> {
                List<DaoStateHash> misMatch = list.stream()
                        .filter(e -> !Arrays.equals(e.getHash(), myDaoStateHash.getHash()))
                        .collect(Collectors.toList());
                daoStateNetworkConsensusMap.put(height, new DaoStateNetworkConsensus(misMatch, list.size()));
            });
        });

        daoStateMonitorListeners.forEach(DaoStateMonitorListener::onDaoStateNetworkConsensusChanged);
    }

    private Optional<DaoStateHash> getMyDaoStateHashByHeight(int height) {
        return daoStateHashChain.stream().filter(e -> e.getBlockHeight() == height).findAny();
    }
}

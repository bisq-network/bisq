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

package bisq.core.network.p2p.inventory;

import bisq.core.dao.monitoring.BlindVoteStateMonitoringService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.ProposalStateMonitoringService;
import bisq.core.dao.monitoring.model.BlindVoteStateBlock;
import bisq.core.dao.monitoring.model.DaoStateBlock;
import bisq.core.dao.monitoring.model.ProposalStateBlock;
import bisq.core.dao.state.DaoStateService;
import bisq.core.network.p2p.inventory.messages.GetInventoryRequest;
import bisq.core.network.p2p.inventory.messages.GetInventoryResponse;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.Statistic;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.MathUtils;
import bisq.common.util.Profiler;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetInventoryRequestHandler implements MessageListener {
    private final NetworkNode networkNode;
    private final P2PDataStorage p2PDataStorage;
    private final DaoStateService daoStateService;
    private final DaoStateMonitoringService daoStateMonitoringService;
    private final ProposalStateMonitoringService proposalStateMonitoringService;
    private final BlindVoteStateMonitoringService blindVoteStateMonitoringService;
    private final Set<String> permittedRequestersPubKey = new HashSet<>();

    @Inject
    public GetInventoryRequestHandler(NetworkNode networkNode,
                                      P2PDataStorage p2PDataStorage,
                                      DaoStateService daoStateService,
                                      DaoStateMonitoringService daoStateMonitoringService,
                                      ProposalStateMonitoringService proposalStateMonitoringService,
                                      BlindVoteStateMonitoringService blindVoteStateMonitoringService) {
        this.networkNode = networkNode;
        this.p2PDataStorage = p2PDataStorage;
        this.daoStateService = daoStateService;
        this.daoStateMonitoringService = daoStateMonitoringService;
        this.proposalStateMonitoringService = proposalStateMonitoringService;
        this.blindVoteStateMonitoringService = blindVoteStateMonitoringService;

        this.networkNode.addMessageListener(this);
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetInventoryRequest) {
            if (permittedRequestersPubKey.isEmpty()) {
                return;
            }

            GetInventoryRequest getInventoryRequest = (GetInventoryRequest) networkEnvelope;
            Map<String, Integer> dataObjects = new HashMap<>();
            p2PDataStorage.getMapForDataResponse(getInventoryRequest.getVersion()).values().stream()
                    .map(e -> e.getClass().getSimpleName())
                    .forEach(className -> {
                        dataObjects.putIfAbsent(className, 0);
                        int prev = dataObjects.get(className);
                        dataObjects.put(className, prev + 1);
                    });
            p2PDataStorage.getMap().values().stream()
                    .map(ProtectedStorageEntry::getProtectedStoragePayload)
                    .filter(Objects::nonNull)
                    .map(e -> e.getClass().getSimpleName())
                    .forEach(className -> {
                        dataObjects.putIfAbsent(className, 0);
                        int prev = dataObjects.get(className);
                        dataObjects.put(className, prev + 1);
                    });
            Map<String, String> inventory = new HashMap<>();
            dataObjects.forEach((key, value) -> inventory.put(key, String.valueOf(value)));

            // DAO data
            int numBsqBlocks = daoStateService.getBlocks().size();
            inventory.put("numBsqBlocks", String.valueOf(numBsqBlocks));

            int daoStateChainHeight = daoStateService.getChainHeight();
            inventory.put("daoStateChainHeight", String.valueOf(daoStateChainHeight));

            LinkedList<DaoStateBlock> daoStateBlockChain = daoStateMonitoringService.getDaoStateBlockChain();
            if (!daoStateBlockChain.isEmpty()) {
                String daoStateHash = Utilities.bytesAsHexString(daoStateBlockChain.getLast().getMyStateHash().getHash());
                inventory.put("daoStateHash", daoStateHash);
            } else {
                inventory.put("daoStateHash", "n/a");
            }

            LinkedList<ProposalStateBlock> proposalStateBlockChain = proposalStateMonitoringService.getProposalStateBlockChain();
            if (!proposalStateBlockChain.isEmpty()) {
                String proposalHash = Utilities.bytesAsHexString(proposalStateBlockChain.getLast().getMyStateHash().getHash());
                inventory.put("proposalHash", proposalHash);
            } else {
                inventory.put("proposalHash", "n/a");
            }

            LinkedList<BlindVoteStateBlock> blindVoteStateBlockChain = blindVoteStateMonitoringService.getBlindVoteStateBlockChain();
            if (!blindVoteStateBlockChain.isEmpty()) {
                String blindVoteHash = Utilities.bytesAsHexString(blindVoteStateBlockChain.getLast().getMyStateHash().getHash());
                inventory.put("blindVoteHash", blindVoteHash);
            } else {
                inventory.put("blindVoteHash", "n/a");
            }

            // P2P network data
            int numConnections = networkNode.getAllConnections().size();
            inventory.put("numConnections", String.valueOf(numConnections));

            long sentBytes = Statistic.totalSentBytesProperty().get();
            inventory.put("sentData", Utilities.readableFileSize(sentBytes));

            long receivedBytes = Statistic.totalReceivedBytesProperty().get();
            inventory.put("receivedData", Utilities.readableFileSize(receivedBytes));

            double receivedMessagesPerSec = MathUtils.roundDouble(Statistic.numTotalReceivedMessagesPerSecProperty().get(), 2);
            inventory.put("receivedMessagesPerSec", String.valueOf(receivedMessagesPerSec));

            double sentMessagesPerSec = MathUtils.roundDouble(Statistic.numTotalSentMessagesPerSecProperty().get(), 2);
            inventory.put("sentMessagesPerSec", String.valueOf(sentMessagesPerSec));

            // JVM info
            long usedMemory = Profiler.getUsedMemoryInMB();
            inventory.put("usedMemory", String.valueOf(usedMemory));

            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            long startTime = runtimeBean.getStartTime();
            inventory.put("jvmStartTime", String.valueOf(startTime));

            log.info("Send inventory {} to {}", inventory, connection.getPeersNodeAddressOptional());
            GetInventoryResponse getInventoryResponse = new GetInventoryResponse(inventory);
            networkNode.sendMessage(connection, getInventoryResponse);
        }
    }

    public void shutDown() {
        networkNode.removeMessageListener(this);
    }

    public void addPermittedRequestersPubKey(String pubKey) {
        permittedRequestersPubKey.add(pubKey);
    }
}

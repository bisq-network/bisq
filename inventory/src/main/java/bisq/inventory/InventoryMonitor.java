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

package bisq.inventory;


import bisq.core.network.p2p.inventory.GetInventoryRequestManager;
import bisq.core.network.p2p.inventory.InventoryItem;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.proto.network.CoreNetworkProtoResolver;

import bisq.network.p2p.NetworkNodeProvider;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.SetupListener;

import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.file.JsonFileManager;
import bisq.common.util.Utilities;

import java.time.Clock;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class InventoryMonitor {

    private final Map<NodeAddress, JsonFileManager> jsonFileManagerByNodeAddress = new HashMap<>();
    private final Map<NodeAddress, List<RequestInfo>> requestInfoListByNode = new HashMap<>();
    private final boolean useLocalhostForP2P;
    private final int intervalSec;

    public InventoryMonitor(File appDir,
                            boolean useLocalhostForP2P,
                            BaseCurrencyNetwork network,
                            int intervalSec,
                            int port) {
        this.useLocalhostForP2P = useLocalhostForP2P;
        this.intervalSec = intervalSec;

        setupCapabilities();

        //TODO until we use all seeds we use our custom seed node file which includes only those which have updated to our branch
        // Once all seeds are updated we can remove that resource file and prefix
        //String fileName = network.name().toLowerCase();
        String networkName = network.name().toLowerCase();
        String fileName = network.isMainnet() ? "inv_" + networkName : networkName;
        DefaultSeedNodeRepository.readSeedNodePropertyFile(fileName)
                .ifPresent(seedNodeFile -> {
                    List<NodeAddress> seedNodes = new ArrayList<>(DefaultSeedNodeRepository.getSeedNodeAddressesFromPropertyFile(fileName));
                    File jsonDir = new File(appDir, "json");
                    if (!jsonDir.exists() && !jsonDir.mkdir()) {
                        log.warn("make jsonDir failed");
                    }
                    seedNodes.forEach(nodeAddress -> {
                        JsonFileManager jsonFileManager = new JsonFileManager(new File(jsonDir, getShortAddress(nodeAddress, useLocalhostForP2P)));
                        jsonFileManagerByNodeAddress.put(nodeAddress, jsonFileManager);
                    });

                    NetworkNode networkNode = getNetworkNode(appDir);

                    GetInventoryRequestManager getInventoryRequestManager = new GetInventoryRequestManager(networkNode);

                    InventoryWebServer inventoryWebServer = new InventoryWebServer(port, seedNodes, seedNodeFile);

                    networkNode.start(new SetupListener() {
                        @Override
                        public void onTorNodeReady() {
                            startRequests(inventoryWebServer, getInventoryRequestManager, seedNodes);
                        }

                        @Override
                        public void onHiddenServicePublished() {
                        }

                        @Override
                        public void onSetupFailed(Throwable throwable) {
                        }

                        @Override
                        public void onRequestCustomBridges() {
                        }
                    });
                });
    }

    public void shutDown() {
        jsonFileManagerByNodeAddress.values().forEach(JsonFileManager::shutDown);
    }

    private NetworkNode getNetworkNode(File appDir) {
        File torDir = new File(appDir, "tor");
        CoreNetworkProtoResolver networkProtoResolver = new CoreNetworkProtoResolver(Clock.systemDefaultZone());
        return new NetworkNodeProvider(networkProtoResolver,
                ArrayList::new,
                useLocalhostForP2P,
                9999,
                torDir,
                null,
                "",
                -1,
                "",
                null,
                false,
                false).get();
    }

    protected void setupCapabilities() {
        Capabilities.app.addAll(
                Capability.TRADE_STATISTICS,
                Capability.TRADE_STATISTICS_2,
                Capability.ACCOUNT_AGE_WITNESS,
                Capability.ACK_MSG,
                Capability.PROPOSAL,
                Capability.BLIND_VOTE,
                Capability.DAO_STATE,
                Capability.BUNDLE_OF_ENVELOPES,
                Capability.MEDIATION,
                Capability.SIGNED_ACCOUNT_AGE_WITNESS,
                Capability.REFUND_AGENT,
                Capability.TRADE_STATISTICS_HASH_UPDATE,
                Capability.NO_ADDRESS_PRE_FIX,
                Capability.TRADE_STATISTICS_3,
                Capability.RECEIVE_BSQ_BLOCK
        );
    }

    private String getShortAddress(NodeAddress nodeAddress, boolean useLocalhostForP2P) {
        return useLocalhostForP2P ?
                nodeAddress.getFullAddress().replace(":", "_") :
                nodeAddress.getFullAddress().substring(0, 10);
    }

    private void startRequests(InventoryWebServer inventoryWebServer,
                               GetInventoryRequestManager getInventoryRequestManager,
                               List<NodeAddress> seedNodes) {
        UserThread.runPeriodically(() ->
                        requestAllSeeds(inventoryWebServer, getInventoryRequestManager, seedNodes),
                intervalSec);

        requestAllSeeds(inventoryWebServer, getInventoryRequestManager, seedNodes);
    }

    private void requestAllSeeds(InventoryWebServer inventoryWebServer,
                                 GetInventoryRequestManager getInventoryRequestManager,
                                 List<NodeAddress> seedNodes) {
        seedNodes.forEach(nodeAddress -> {
            RequestInfo requestInfo = new RequestInfo(System.currentTimeMillis());
            new Thread(() -> {
                Thread.currentThread().setName("request @ " + getShortAddress(nodeAddress, useLocalhostForP2P));
                getInventoryRequestManager.request(nodeAddress,
                        result -> {
                            log.info("nodeAddress={}, result={}", nodeAddress, result.toString());
                            long responseTime = System.currentTimeMillis();
                            requestInfo.setResponseTime(responseTime);
                            requestInfo.setInventory(result);

                            requestInfoListByNode.putIfAbsent(nodeAddress, new ArrayList<>());
                            List<RequestInfo> requestInfoList = requestInfoListByNode.get(nodeAddress);
                            requestInfoList.add(requestInfo);

                            // We create average of all nodes latest results. It might be that the nodes last result is
                            // from a previous request as the response has not arrived yet.
                            Set<RequestInfo> requestInfoSetOfOtherNodes = requestInfoListByNode.values().stream()
                                    .filter(list -> !list.isEmpty())
                                    .map(list -> list.get(list.size() - 1))
                                    .collect(Collectors.toSet());
                            Map<InventoryItem, Double> averageValues = getAverageValues(requestInfoSetOfOtherNodes);

                            inventoryWebServer.onNewRequestInfo(requestInfoListByNode, averageValues);

                            String json = Utilities.objectToJson(requestInfo);
                            jsonFileManagerByNodeAddress.get(nodeAddress).writeToDisc(json, String.valueOf(responseTime));
                        },
                        errorMessage -> {
                            log.warn(errorMessage);
                            requestInfo.setErrorMessage(errorMessage);
                        });
            }).start();

        });
    }

    private Map<InventoryItem, Double> getAverageValues(Set<RequestInfo> requestInfoSetOfOtherNodes) {
        Map<InventoryItem, Double> averageValuesPerItem = new HashMap<>();
        Arrays.asList(InventoryItem.values()).forEach(inventoryItem -> {
            if (inventoryItem.getType().equals(Integer.class)) {
                averageValuesPerItem.put(inventoryItem, getAverageFromIntegerValues(requestInfoSetOfOtherNodes, inventoryItem));
            } else if (inventoryItem.getType().equals(Long.class)) {
                averageValuesPerItem.put(inventoryItem, getAverageFromLongValues(requestInfoSetOfOtherNodes, inventoryItem));
            } else if (inventoryItem.getType().equals(Double.class)) {
                averageValuesPerItem.put(inventoryItem, getAverageFromDoubleValues(requestInfoSetOfOtherNodes, inventoryItem));
            }
            // If type of value is String we ignore it
        });
        return averageValuesPerItem;
    }

    private double getAverageFromIntegerValues(Set<RequestInfo> requestInfoSetOfOtherNodes,
                                               InventoryItem inventoryItem) {
        checkArgument(inventoryItem.getType().equals(Integer.class));
        return requestInfoSetOfOtherNodes.stream()
                .map(RequestInfo::getInventory)
                .filter(inventory -> inventory.containsKey(inventoryItem))
                .mapToInt(inventory -> Integer.parseInt(inventory.get(inventoryItem)))
                .average()
                .orElse(0d);
    }

    private double getAverageFromLongValues(Set<RequestInfo> requestInfoSetOfOtherNodes,
                                            InventoryItem inventoryItem) {
        checkArgument(inventoryItem.getType().equals(Long.class));
        return requestInfoSetOfOtherNodes.stream()
                .map(RequestInfo::getInventory)
                .filter(inventory -> inventory.containsKey(inventoryItem))
                .mapToLong(inventory -> Long.parseLong(inventory.get(inventoryItem)))
                .average()
                .orElse(0d);
    }

    private double getAverageFromDoubleValues(Set<RequestInfo> requestInfoSetOfOtherNodes,
                                              InventoryItem inventoryItem) {
        checkArgument(inventoryItem.getType().equals(Double.class));
        return requestInfoSetOfOtherNodes.stream()
                .map(RequestInfo::getInventory)
                .filter(inventory -> inventory.containsKey(inventoryItem))
                .mapToDouble(inventory -> Double.parseDouble((inventory.get(inventoryItem))))
                .average()
                .orElse(0d);
    }
}

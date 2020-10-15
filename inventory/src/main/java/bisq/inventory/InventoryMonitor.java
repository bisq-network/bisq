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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InventoryMonitor {

    private final Map<NodeAddress, JsonFileManager> jsonFileManagerByNodeAddress = new HashMap<>();
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

        DefaultSeedNodeRepository.readSeedNodePropertyFile(network)
                .ifPresent(seedNodeFile -> {
                    List<NodeAddress> seedNodes = new ArrayList<>(DefaultSeedNodeRepository.getSeedNodeAddressesFromPropertyFile(network));
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

                            inventoryWebServer.onNewRequestInfo(requestInfo, nodeAddress);

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

    public void shutDown() {
        jsonFileManagerByNodeAddress.values().forEach(JsonFileManager::shutDown);
    }

    @Getter
    static class RequestInfo {
        private final long requestStartTime;
        @Setter
        private long responseTime;
        @Setter
        private Map<String, String> inventory;
        @Setter
        private String errorMessage;

        public RequestInfo(long requestStartTime) {
            this.requestStartTime = requestStartTime;
        }
    }
}

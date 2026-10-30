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


import bisq.core.app.TorSetup;
import bisq.core.network.p2p.inventory.GetInventoryRequestManager;
import bisq.core.network.p2p.inventory.model.Average;
import bisq.core.network.p2p.inventory.model.DeviationSeverity;
import bisq.core.network.p2p.inventory.model.InventoryItem;
import bisq.core.network.p2p.inventory.model.RequestInfo;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.network.LocalhostNetworkNode;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.NewTor;
import bisq.network.p2p.network.SetupListener;
import bisq.network.p2p.network.TorNetworkNode;
import bisq.network.p2p.network.TorrcSettings;

import bisq.common.UserThread;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.file.JsonFileManager;
import bisq.common.util.Tuple2;

import java.time.Clock;

import java.io.BufferedReader;
import java.io.File;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

@Slf4j
public class InventoryMonitor implements SetupListener {
    // The arguments of InventoryMonitorMain are positional, so the error of an unusable sandbox names the argument
    // instead of the option --useTorSandbox of the other applications.
    static final String TOR_SANDBOX_OPT_OUT_HINT = "Start the inventory monitor with 0 as the seventh argument " +
            "(useTorSandbox) to run Tor without the sandbox.";

    private final Map<NodeAddress, JsonFileManager> jsonFileManagerByNodeAddress = new HashMap<>();
    private final Map<NodeAddress, List<RequestInfo>> requestInfoListByNode = new HashMap<>();
    private final File appDir;
    private final boolean useLocalhostForP2P;
    private final int intervalSec;
    private final NetworkNode networkNode;
    private final GetInventoryRequestManager getInventoryRequestManager;
    private final Runnable setupFailedHandler;

    private final List<NodeAddress> seedNodes;
    private InventoryWebServer inventoryWebServer;
    private int requestCounter = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public InventoryMonitor(File appDir,
                            boolean useLocalhostForP2P,
                            BaseCurrencyNetwork network,
                            int intervalSec,
                            boolean cleanupTorFiles,
                            boolean useTorSandbox,
                            Runnable setupFailedHandler) {
        this.appDir = appDir;
        this.useLocalhostForP2P = useLocalhostForP2P;
        this.intervalSec = intervalSec;
        this.setupFailedHandler = setupFailedHandler;

        File torDir = new File(appDir, "tor");
        if (!torDir.exists() && !torDir.mkdir()) {
            log.warn("make torDir failed");
        }

        networkNode = getNetworkNode(torDir, useTorSandbox);
        getInventoryRequestManager = new GetInventoryRequestManager(networkNode);

        // The seed node files are named after the network in lower case. In the default locale, a Turkish one would
        // turn the I of BTC_MAINNET into a dotless i.
        String networkName = network.name().toLowerCase(Locale.ROOT);
        String fileName = network.isMainnet() ? "inv_" + networkName : networkName;
        seedNodes = new ArrayList<>(DefaultSeedNodeRepository.getSeedNodeAddressesFromPropertyFile(fileName));
        addJsonFileManagers(seedNodes);

        BufferedReader bufferedReader = DefaultSeedNodeRepository.readSeedNodePropertyFile(fileName).orElseThrow();
        inventoryWebServer = new InventoryWebServer(seedNodes, bufferedReader);

        TorSetup torSetup = new TorSetup(torDir);
        if (cleanupTorFiles) {
            torSetup.cleanupTorFiles(() -> {
            }, log::error);
        }
    }

    void start(int port) {
        networkNode.start(this);
        inventoryWebServer.start(port);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown(Runnable shutDownCompleteHandler) {
        networkNode.shutDown(shutDownCompleteHandler);
        inventoryWebServer.shutDown();
        jsonFileManagerByNodeAddress.values().forEach(JsonFileManager::shutDown);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onTorNodeReady() {
        UserThread.runPeriodically(this::requestFromAllSeeds, intervalSec);
        requestFromAllSeeds();
    }

    @Override
    public void onHiddenServicePublished() {
    }

    // Nothing retries a failed start of Tor or of the onion service. Without Tor the monitor would report every seed
    // node as unreachable until its periodic restart.
    @Override
    public void onSetupFailed(Throwable throwable) {
        log.error("The start of Tor or of the onion service failed. We shut down, so that the inventory monitor " +
                "gets restarted.");
        setupFailedHandler.run();
    }

    @Override
    public void onRequestCustomBridges() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestFromAllSeeds() {
        requestCounter++;
        seedNodes.forEach(nodeAddress -> {
            RequestInfo requestInfo = new RequestInfo(System.currentTimeMillis());
            new Thread(() -> {
                Thread.currentThread().setName("request @ " + getShortAddress(nodeAddress, useLocalhostForP2P));
                getInventoryRequestManager.request(nodeAddress,
                        result -> processResponse(nodeAddress, requestInfo, result, null),
                        errorMessage -> processResponse(nodeAddress, requestInfo, null, errorMessage));
            }).start();
        });
    }

    private void processResponse(NodeAddress nodeAddress,
                                 RequestInfo requestInfo,
                                 @Nullable Map<InventoryItem, String> result,
                                 @Nullable String errorMessage) {
        if (errorMessage != null && !errorMessage.isEmpty()) {
            log.warn("Error at connection to peer {}: {}", nodeAddress, errorMessage);
            requestInfo.setErrorMessage(errorMessage);
        } else {
            requestInfo.setResponseTime(System.currentTimeMillis());
        }

        boolean ignoreDeviationAtStartup;
        if (result != null) {
            log.info("nodeAddress={}, result={}", nodeAddress, result.toString());

            // If seed just started up we ignore the deviation as it can be expected that seed is still syncing
            // DAO state/blocks. P2P data should be ready but as we received it from other seeds it is not that
            // valuable information either, so we apply the ignore to all data.
            if (result.containsKey(InventoryItem.jvmStartTime)) {
                String jvmStartTimeString = result.get(InventoryItem.jvmStartTime);
                long jvmStartTime = Long.parseLong(jvmStartTimeString);
                ignoreDeviationAtStartup = jvmStartTime < TimeUnit.MINUTES.toMillis(2);
            } else {
                ignoreDeviationAtStartup = false;
            }
        } else {
            ignoreDeviationAtStartup = false;
        }

        requestInfoListByNode.putIfAbsent(nodeAddress, new ArrayList<>());
        List<RequestInfo> requestInfoList = requestInfoListByNode.get(nodeAddress);


        // We create average of all nodes latest results. It might be that the nodes last result is
        // from a previous request as the response has not arrived yet.
        //TODO might be not a good idea to use the last result if its not a recent one. a faulty node would distort
        // the average calculation.
        // As we add at the end our own result the average is excluding our own value
        Collection<List<RequestInfo>> requestInfoListByNodeValues = requestInfoListByNode.values();
        Set<RequestInfo> requestInfoSet = requestInfoListByNodeValues.stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(list.size() - 1))
                .collect(Collectors.toSet());
        Map<InventoryItem, Double> averageValues = Average.of(requestInfoSet);

        String daoStateChainHeight = result != null &&
                result.containsKey(InventoryItem.daoStateChainHeight) ?
                result.get(InventoryItem.daoStateChainHeight) :
                null;
        List.of(InventoryItem.values()).forEach(inventoryItem -> {
            String value = result != null ? result.get(inventoryItem) : null;
            Tuple2<Double, Double> tuple = inventoryItem.getDeviationAndAverage(averageValues, value);
            Double deviation = tuple != null ? tuple.first : null;
            Double average = tuple != null ? tuple.second : null;
            DeviationSeverity deviationSeverity = ignoreDeviationAtStartup ? DeviationSeverity.IGNORED :
                    inventoryItem.getDeviationSeverity(deviation,
                            requestInfoListByNodeValues,
                            value,
                            daoStateChainHeight);
            int endIndex = Math.max(0, requestInfoList.size() - 1);
            int deviationTolerance = inventoryItem.getDeviationTolerance();
            int fromIndex = Math.max(0, endIndex - deviationTolerance);
            List<DeviationSeverity> lastDeviationSeverityEntries = requestInfoList.subList(fromIndex, endIndex).stream()
                    .filter(e -> e.getDataMap().containsKey(inventoryItem))
                    .map(e -> e.getDataMap().get(inventoryItem).getDeviationSeverity())
                    .collect(Collectors.toList());
            long numWarnings = lastDeviationSeverityEntries.stream()
                    .filter(e -> e == DeviationSeverity.WARN)
                    .count();
            long numAlerts = lastDeviationSeverityEntries.stream()
                    .filter(e -> e == DeviationSeverity.ALERT)
                    .count();
            boolean persistentWarning = numWarnings == deviationTolerance;
            boolean persistentAlert = numAlerts == deviationTolerance;
            RequestInfo.Data data = new RequestInfo.Data(value, average, deviation, deviationSeverity, persistentWarning, persistentAlert);
            requestInfo.getDataMap().put(inventoryItem, data);
        });

        requestInfoList.add(requestInfo);

        inventoryWebServer.onNewRequestInfo(requestInfoListByNode, requestCounter);

        String json = JsonUtil.objectToJson(requestInfo);
        jsonFileManagerByNodeAddress.get(nodeAddress).writeToDisc(json, String.valueOf(requestInfo.getRequestStartTime()));
    }

    private void addJsonFileManagers(List<NodeAddress> seedNodes) {
        File jsonDir = new File(appDir, "json");
        if (!jsonDir.exists() && !jsonDir.mkdir()) {
            log.warn("make jsonDir failed");
        }
        seedNodes.forEach(nodeAddress -> {
            JsonFileManager jsonFileManager = new JsonFileManager(new File(jsonDir, getShortAddress(nodeAddress, useLocalhostForP2P)));
            jsonFileManagerByNodeAddress.put(nodeAddress, jsonFileManager);
        });
    }

    // As every Bisq application, the inventory monitor runs its Tor in the sandbox unless the user turns it off.
    private NetworkNode getNetworkNode(File torDir, boolean useTorSandbox) {
        CoreNetworkProtoResolver networkProtoResolver = new CoreNetworkProtoResolver(Clock.systemDefaultZone());
        if (useLocalhostForP2P) {
            return new LocalhostNetworkNode(9999, networkProtoResolver, null, 12);
        }
        NewTor torMode = new NewTor(torDir,
                TorrcSettings.fromCommandLine(null, ""),
                BridgeAddressProvider.NONE,
                useTorSandbox,
                false,
                TOR_SANDBOX_OPT_OUT_HINT);
        return new TorNetworkNode(9999, networkProtoResolver, false, torMode, null, 12);
    }

    private String getShortAddress(NodeAddress nodeAddress, boolean useLocalhostForP2P) {
        return useLocalhostForP2P ?
                nodeAddress.getFullAddress().replace(":", "_") :
                nodeAddress.getFullAddress().substring(0, 10);
    }
}

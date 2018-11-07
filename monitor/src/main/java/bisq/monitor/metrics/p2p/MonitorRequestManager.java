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

package bisq.monitor.metrics.p2p;

import bisq.monitor.MonitorOptionKeys;
import bisq.monitor.metrics.Metrics;
import bisq.monitor.metrics.MetricsModel;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.seed.SeedNodeRepository;
import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.Timer;
import bisq.common.UserThread;

import net.gpedro.integrations.slack.SlackApi;
import net.gpedro.integrations.slack.SlackMessage;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorRequestManager implements ConnectionListener {
    private static final long RETRY_DELAY_SEC = 30;
    private static final long CLEANUP_TIMER = 60;
    private static final long REQUEST_PERIOD_MIN = 10;
    private static final int MAX_RETRIES = 5;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final int numNodes;

    private SlackApi slackApi;
    private P2PDataStorage dataStorage;
    private SeedNodeRepository seedNodeRepository;
    private MetricsModel metricsModel;
    private final Set<NodeAddress> seedNodeAddresses;

    private final Map<NodeAddress, MonitorRequestHandler> handlerMap = new HashMap<>();
    private Map<NodeAddress, Timer> retryTimerMap = new HashMap<>();
    private Map<NodeAddress, Integer> retryCounterMap = new HashMap<>();
    private boolean stopped;
    private int completedRequestIndex;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MonitorRequestManager(NetworkNode networkNode,
                                 P2PDataStorage dataStorage,
                                 SeedNodeRepository seedNodeRepository,
                                 MetricsModel metricsModel,
                                 @Named(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL) String slackUrlSeedChannel) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.seedNodeRepository = seedNodeRepository;
        this.metricsModel = metricsModel;

        if (!slackUrlSeedChannel.isEmpty())
            slackApi = new SlackApi(slackUrlSeedChannel);
        this.networkNode.addConnectionListener(this);

        seedNodeAddresses = new HashSet<>(seedNodeRepository.getSeedNodeAddresses());
        seedNodeAddresses.stream().forEach(nodeAddress -> metricsModel.addToMap(nodeAddress, new Metrics()));
        numNodes = seedNodeAddresses.size();
    }

    public void shutDown() {
        stopped = true;
        stopAllRetryTimers();
        networkNode.removeConnectionListener(this);
        closeAllHandlers();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        requestAllNodes();
        UserThread.runPeriodically(this::requestAllNodes, REQUEST_PERIOD_MIN, TimeUnit.MINUTES);

        // We want to update the data for the btc nodes more frequently
        UserThread.runPeriodically(metricsModel::updateReport, 10);
    }

    private void requestAllNodes() {
        stopAllRetryTimers();
        closeAllConnections();
        // we give 1 sec. for all connection shutdown
        final int[] delay = {1000};
        metricsModel.setLastCheckTs(System.currentTimeMillis());

        seedNodeAddresses.stream().forEach(nodeAddress -> {
            UserThread.runAfter(() -> requestFromNode(nodeAddress), delay[0], TimeUnit.MILLISECONDS);
            delay[0] += 100;
        });
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
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RequestData
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestFromNode(NodeAddress nodeAddress) {
        if (!stopped) {
            if (!handlerMap.containsKey(nodeAddress)) {
                final Metrics metrics = metricsModel.getMetrics(nodeAddress);
                MonitorRequestHandler requestDataHandler = new MonitorRequestHandler(networkNode,
                        dataStorage,
                        metrics,
                        new MonitorRequestHandler.Listener() {
                            @Override
                            public void onComplete() {
                                log.trace("RequestDataHandshake of outbound connection complete. nodeAddress={}",
                                        nodeAddress);
                                stopRetryTimer(nodeAddress);
                                retryCounterMap.remove(nodeAddress);
                                metrics.setNumRequestAttempts(retryCounterMap.getOrDefault(nodeAddress, 1));

                                // need to remove before listeners are notified as they cause the update call
                                handlerMap.remove(nodeAddress);

                                metricsModel.updateReport();
                                completedRequestIndex++;
                                if (completedRequestIndex == numNodes)
                                    metricsModel.log();

                                if (metricsModel.getNodesInError().contains(nodeAddress)) {
                                    metricsModel.removeNodesInError(nodeAddress);
                                    if (slackApi != null)
                                        slackApi.call(new SlackMessage("Fixed: " + nodeAddress.getFullAddress(),
                                                "<" + seedNodeRepository.getOperator(nodeAddress) + ">" + " Your seed node is recovered."));
                                }
                            }

                            @Override
                            public void onFault(String errorMessage, NodeAddress nodeAddress) {
                                handlerMap.remove(nodeAddress);
                                stopRetryTimer(nodeAddress);

                                int retryCounter = retryCounterMap.getOrDefault(nodeAddress, 0);
                                metrics.setNumRequestAttempts(retryCounter);
                                if (retryCounter < MAX_RETRIES) {
                                    log.info("We got an error at peer={}. We will try again after a delay of {} sec. error={} ",
                                            nodeAddress, RETRY_DELAY_SEC, errorMessage);
                                    final Timer timer = UserThread.runAfter(() -> requestFromNode(nodeAddress), RETRY_DELAY_SEC);
                                    retryTimerMap.put(nodeAddress, timer);
                                    retryCounterMap.put(nodeAddress, ++retryCounter);
                                } else {
                                    log.warn("We got repeated errors at peer={}. error={} ",
                                            nodeAddress, errorMessage);

                                    metricsModel.addNodesInError(nodeAddress);
                                    metrics.getErrorMessages().add(errorMessage + " (" + new Date().toString() + ")");

                                    metricsModel.updateReport();
                                    completedRequestIndex++;
                                    if (completedRequestIndex == numNodes)
                                        metricsModel.log();

                                    retryCounterMap.remove(nodeAddress);

                                    if (slackApi != null)
                                        slackApi.call(new SlackMessage("Error: " + nodeAddress.getFullAddress(),
                                                "<" + seedNodeRepository.getOperator(nodeAddress) + ">" + " Your seed node failed " + RETRY_DELAY_SEC + " times with error message: " + errorMessage));
                                }
                            }
                        });
                handlerMap.put(nodeAddress, requestDataHandler);
                requestDataHandler.requestData(nodeAddress);
            } else {
                log.warn("We have started already a requestDataHandshake to peer. nodeAddress=" + nodeAddress + "\n" +
                        "We start a cleanup timer if the handler has not closed by itself in between 2 minutes.");

                UserThread.runAfter(() -> {
                    if (handlerMap.containsKey(nodeAddress)) {
                        MonitorRequestHandler handler = handlerMap.get(nodeAddress);
                        handler.stop();
                        handlerMap.remove(nodeAddress);
                    }
                }, CLEANUP_TIMER);
            }
        } else {
            log.warn("We have stopped already. We ignore that requestData call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void closeAllConnections() {
        networkNode.getAllConnections().stream().forEach(connection -> connection.shutDown(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER));
    }

    private void stopAllRetryTimers() {
        retryTimerMap.values().stream().forEach(Timer::stop);
        retryTimerMap.clear();

        retryCounterMap.clear();
    }

    private void stopRetryTimer(NodeAddress nodeAddress) {
        retryTimerMap.entrySet().stream()
                .filter(e -> e.getKey().equals(nodeAddress))
                .forEach(e -> e.getValue().stop());
        retryTimerMap.remove(nodeAddress);
    }

    private void closeHandler(Connection connection) {
        Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
        if (peersNodeAddressOptional.isPresent()) {
            NodeAddress nodeAddress = peersNodeAddressOptional.get();
            if (handlerMap.containsKey(nodeAddress)) {
                handlerMap.get(nodeAddress).cancel();
                handlerMap.remove(nodeAddress);
            }
        } else {
            log.trace("closeRequestDataHandler: nodeAddress not set in connection " + connection);
        }
    }

    private void closeAllHandlers() {
        handlerMap.values().stream().forEach(MonitorRequestHandler::cancel);
        handlerMap.clear();
    }

}

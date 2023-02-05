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

package bisq.seednode.reporting;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.monitoring.BlindVoteStateMonitoringService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.ProposalStateMonitoringService;
import bisq.core.dao.monitoring.model.BlindVoteStateBlock;
import bisq.core.dao.monitoring.model.DaoStateBlock;
import bisq.core.dao.monitoring.model.ProposalStateBlock;
import bisq.core.dao.monitoring.network.StateNetworkService;
import bisq.core.dao.monitoring.network.messages.GetBlindVoteStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetProposalStateHashesRequest;
import bisq.core.dao.node.full.network.FullNodeNetworkService;
import bisq.core.dao.node.messages.GetBlocksRequest;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.Statistic;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.RequestDataManager;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.util.Profiler;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import java.lang.management.ManagementFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends reporting data to monitoring server via clear net.
 * The seed node is configured with nginx as proxy which takes care of TLS handling and provides the client key.
 *
 * We send on a regular interval every 60 seconds the used memory metric data which serves as a heartbeat to signal the
 * monitor that the seed node is alive.
 * We send every 5 minutes the network data, network load data and node specific data.
 * At each new block we send the DAO hashes and block height.
 */
@Slf4j
@Singleton
public class SeedNodeReportingService {
    private final static long REPORT_DELAY_SEC = TimeUnit.MINUTES.toSeconds(5);
    private final static long HEART_BEAT_DELAY_SEC = TimeUnit.MINUTES.toSeconds(1);

    private final P2PService p2PService;
    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final P2PDataStorage p2PDataStorage;
    private final DaoStateService daoStateService;
    private final DaoStateMonitoringService daoStateMonitoringService;
    private final ProposalStateMonitoringService proposalStateMonitoringService;
    private final BlindVoteStateMonitoringService blindVoteStateMonitoringService;
    private final int maxConnections;
    private final String seedNodeReportingServerUrl;
    private final DaoStateListener daoStateListener;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Timer heartBeatTimer;
    private Timer dataReportTimer;

    @Inject
    public SeedNodeReportingService(P2PService p2PService,
                                    DaoFacade daoFacade,
                                    NetworkNode networkNode,
                                    PeerManager peerManager,
                                    P2PDataStorage p2PDataStorage,
                                    DaoStateService daoStateService,
                                    DaoStateMonitoringService daoStateMonitoringService,
                                    ProposalStateMonitoringService proposalStateMonitoringService,
                                    BlindVoteStateMonitoringService blindVoteStateMonitoringService,
                                    RequestDataManager requestDataManager,
                                    FullNodeNetworkService fullNodeNetworkService,
                                    @Named(Config.MAX_CONNECTIONS) int maxConnections,
                                    @Named(Config.SEED_NODE_REPORTING_SERVER_URL) String seedNodeReportingServerUrl) {
        this.p2PService = p2PService;
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.p2PDataStorage = p2PDataStorage;
        this.daoStateService = daoStateService;
        this.daoStateMonitoringService = daoStateMonitoringService;
        this.proposalStateMonitoringService = proposalStateMonitoringService;
        this.blindVoteStateMonitoringService = blindVoteStateMonitoringService;
        this.maxConnections = maxConnections;
        this.seedNodeReportingServerUrl = seedNodeReportingServerUrl;

        // The pool size must be larger as the expected parallel sends because HttpClient use it
        // internally for asynchronous and dependent tasks.
        executor = Utilities.getThreadPoolExecutor("SeedNodeReportingService", 20, 40, 100, 8 * 60);
        httpClient = HttpClient.newBuilder().executor(executor).build();

        heartBeatTimer = UserThread.runPeriodically(this::sendHeartBeat, HEART_BEAT_DELAY_SEC);

        daoStateListener = new DaoStateListener() {
            @Override
            public void onParseBlockChainComplete() {
                daoFacade.removeBsqStateListener(daoStateListener);
                dataReportTimer = UserThread.runPeriodically(() -> sendDataReport(), REPORT_DELAY_SEC);
                sendDataReport();
                sendBlockRelatedData();

                // We send each time when a new block is received and the DAO hash has been provided (which
                // takes a bit after the block arrives).
                daoStateMonitoringService.addListener(new DaoStateMonitoringService.Listener() {
                    @Override
                    public void onDaoStateBlockCreated() {
                        sendBlockRelatedData();
                    }
                });
            }
        };
        daoFacade.addBsqStateListener(daoStateListener);

        p2PService.getNetworkNode().addMessageListener((networkEnvelope, connection) -> {
            if (networkEnvelope instanceof PreliminaryGetDataRequest ||
                    networkEnvelope instanceof GetUpdatedDataRequest ||
                    networkEnvelope instanceof GetBlocksRequest ||
                    networkEnvelope instanceof GetDaoStateHashesRequest ||
                    networkEnvelope instanceof GetProposalStateHashesRequest ||
                    networkEnvelope instanceof GetBlindVoteStateHashesRequest) {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                int serializedSize = networkEnvelope.toProtoNetworkEnvelope().getSerializedSize();
                String simpleName = networkEnvelope.getClass().getSimpleName();
                try {
                    LongValueReportingItem reportingItem = LongValueReportingItem.valueOf(simpleName);
                    reportingItems.add(reportingItem.withValue(serializedSize));
                    sendReportingItems(reportingItems);
                } catch (Throwable t) {
                    log.warn("Could not find enum for {}. Error={}", simpleName, t);
                }
            }
        });

        requestDataManager.addResponseListener(new RequestDataManager.ResponseListener() {
            @Override
            public void onSuccess(int serializedSize) {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetDataResponse.withValue(serializedSize));
                sendReportingItems(reportingItems);
            }

            @Override
            public void onFault() {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetDataResponse.withValue(-1));
                sendReportingItems(reportingItems);
            }
        });

        fullNodeNetworkService.addResponseListener(new FullNodeNetworkService.ResponseListener() {
            @Override
            public void onSuccess(int serializedSize) {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetBlocksResponse.withValue(serializedSize));
                sendReportingItems(reportingItems);
            }

            @Override
            public void onFault() {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetBlocksResponse.withValue(-1));
                sendReportingItems(reportingItems);
            }
        });

        daoStateMonitoringService.addResponseListener(new StateNetworkService.ResponseListener() {
            @Override
            public void onSuccess(int serializedSize) {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetDaoStateHashesResponse.withValue(serializedSize));
                sendReportingItems(reportingItems);
            }

            @Override
            public void onFault() {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetDaoStateHashesResponse.withValue(-1));
                sendReportingItems(reportingItems);
            }
        });

        proposalStateMonitoringService.addResponseListener(new StateNetworkService.ResponseListener() {
            @Override
            public void onSuccess(int serializedSize) {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetProposalStateHashesResponse.withValue(serializedSize));
                sendReportingItems(reportingItems);
            }

            @Override
            public void onFault() {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetProposalStateHashesResponse.withValue(-1));
                sendReportingItems(reportingItems);
            }
        });

        blindVoteStateMonitoringService.addResponseListener(new StateNetworkService.ResponseListener() {
            @Override
            public void onSuccess(int serializedSize) {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetBlindVoteStateHashesResponse.withValue(serializedSize));
                sendReportingItems(reportingItems);
            }

            @Override
            public void onFault() {
                ReportingItems reportingItems = new ReportingItems(getMyAddress());
                reportingItems.add(LongValueReportingItem.GetBlindVoteStateHashesResponse.withValue(-1));
                sendReportingItems(reportingItems);
            }
        });
    }

    public void shutDown() {
        if (heartBeatTimer != null) {
            heartBeatTimer.stop();
        }
        if (dataReportTimer != null) {
            dataReportTimer.stop();
        }

        Utilities.shutdownAndAwaitTermination(executor, 4, TimeUnit.SECONDS);
    }

    private void sendHeartBeat() {
        if (p2PService.getAddress() == null) {
            return;
        }
        ReportingItems reportingItems = new ReportingItems(getMyAddress());
        reportingItems.add(LongValueReportingItem.usedMemoryInMB.withValue(Profiler.getUsedMemoryInMB()));
        sendReportingItems(reportingItems);
    }

    private void sendBlockRelatedData() {
        if (p2PService.getAddress() == null) {
            return;
        }

        ReportingItems reportingItems = new ReportingItems(getMyAddress());
        int daoStateChainHeight = daoStateService.getChainHeight();
        reportingItems.add(LongValueReportingItem.daoStateChainHeight.withValue(daoStateChainHeight));
        daoStateService.getLastBlock().map(block -> (block.getTime() / 1000))
                .ifPresent(blockTime -> reportingItems.add(LongValueReportingItem.blockTimeIsSec.withValue(blockTime)));
        LinkedList<DaoStateBlock> daoStateBlockChain = daoStateMonitoringService.getDaoStateBlockChain();
        if (!daoStateBlockChain.isEmpty()) {
            String daoStateHash = Utilities.bytesAsHexString(daoStateBlockChain.getLast().getMyStateHash().getHash());
            reportingItems.add(StringValueReportingItem.daoStateHash.withValue(daoStateHash));
        }

        LinkedList<ProposalStateBlock> proposalStateBlockChain = proposalStateMonitoringService.getProposalStateBlockChain();
        if (!proposalStateBlockChain.isEmpty()) {
            String proposalHash = Utilities.bytesAsHexString(proposalStateBlockChain.getLast().getMyStateHash().getHash());
            reportingItems.add(StringValueReportingItem.proposalHash.withValue(proposalHash));
        }

        LinkedList<BlindVoteStateBlock> blindVoteStateBlockChain = blindVoteStateMonitoringService.getBlindVoteStateBlockChain();
        if (!blindVoteStateBlockChain.isEmpty()) {
            String blindVoteHash = Utilities.bytesAsHexString(blindVoteStateBlockChain.getLast().getMyStateHash().getHash());
            reportingItems.add(StringValueReportingItem.blindVoteHash.withValue(blindVoteHash));
        }

        sendReportingItems(reportingItems);
    }

    private void sendDataReport() {
        if (p2PService.getAddress() == null) {
            return;
        }

        ReportingItems reportingItems = new ReportingItems(getMyAddress());

        // Data
        Map<String, Integer> numItemsByType = new HashMap<>();
        Stream.concat(p2PDataStorage.getPersistableNetworkPayloadCollection().stream()
                                .map(payload -> payload.getClass().getSimpleName()),
                        p2PDataStorage.getMap().values().stream()
                                .map(ProtectedStorageEntry::getProtectedStoragePayload)
                                .map(payload -> payload.getClass().getSimpleName()))
                .forEach(className -> {
                    numItemsByType.putIfAbsent(className, 0);
                    numItemsByType.put(className, numItemsByType.get(className) + 1);
                });
        numItemsByType.forEach((key, value) -> LongValueReportingItem.from(key, value).ifPresent(reportingItems::add));

        // Network
        reportingItems.add(LongValueReportingItem.numConnections.withValue(networkNode.getAllConnections().size()));
        reportingItems.add(LongValueReportingItem.peakNumConnections.withValue(peerManager.getPeakNumConnections()));
        reportingItems.add(LongValueReportingItem.numAllConnectionsLostEvents.withValue(peerManager.getNumAllConnectionsLostEvents()));
        reportingItems.add(LongValueReportingItem.sentBytes.withValue(Statistic.getTotalSentBytes()));
        reportingItems.add(LongValueReportingItem.receivedBytes.withValue(Statistic.getTotalReceivedBytes()));
        reportingItems.add(DoubleValueReportingItem.sentBytesPerSec.withValue(Statistic.getTotalSentBytesPerSec()));
        reportingItems.add(DoubleValueReportingItem.sentMessagesPerSec.withValue(Statistic.getNumTotalSentMessagesPerSec()));
        reportingItems.add(DoubleValueReportingItem.receivedBytesPerSec.withValue(Statistic.getTotalReceivedBytesPerSec()));
        reportingItems.add(DoubleValueReportingItem.receivedMessagesPerSec.withValue(Statistic.numTotalReceivedMessagesPerSec()));

        // Node
        reportingItems.add(LongValueReportingItem.usedMemoryInMB.withValue(Profiler.getUsedMemoryInMB()));
        reportingItems.add(LongValueReportingItem.totalMemoryInMB.withValue(Profiler.getTotalMemoryInMB()));
        reportingItems.add(LongValueReportingItem.jvmStartTimeInSec.withValue((ManagementFactory.getRuntimeMXBean().getStartTime() / 1000)));
        reportingItems.add(LongValueReportingItem.maxConnections.withValue(maxConnections));
        reportingItems.add(StringValueReportingItem.version.withValue(Version.VERSION));

        Version.findCommitHash().ifPresent(commitHash -> reportingItems.add(StringValueReportingItem.commitHash.withValue(commitHash)));

        sendReportingItems(reportingItems);
    }

    private void sendReportingItems(ReportingItems reportingItems) {
        String truncated = Utilities.toTruncatedString(reportingItems.toString());
        try {
            log.info("Going to send report to monitor server: {}", truncated);
            // We send the data as hex encoded protobuf data. We do not use the envelope as it is not part of the p2p system.
            byte[] protoMessageAsBytes = reportingItems.toProtoMessageAsBytes();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(seedNodeReportingServerUrl))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(protoMessageAsBytes))
                    .header("User-Agent", getMyAddress())
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    log.warn("Exception at sending reporting data. {}", throwable.getMessage());
                } else if (response.statusCode() == 200) {
                    log.info("Sent successfully report to monitor server with {} items", reportingItems.size());
                } else {
                    log.warn("Server responded with error. Response={}", response);
                }
            });
        } catch (RejectedExecutionException t) {
            log.warn("Did not send reportingItems {} because of RejectedExecutionException {}", truncated, t.toString());
        } catch (Throwable t) {
            log.warn("Did not send reportingItems {} because of exception {}", truncated, t.toString());
        }
    }

    private String getMyAddress() {
        return p2PService.getAddress() != null ? p2PService.getAddress().getFullAddress() : "N/A";
    }

}

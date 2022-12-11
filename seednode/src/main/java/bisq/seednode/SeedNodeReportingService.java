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

package bisq.seednode;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.monitoring.BlindVoteStateMonitoringService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.ProposalStateMonitoringService;
import bisq.core.dao.monitoring.model.BlindVoteStateBlock;
import bisq.core.dao.monitoring.model.DaoStateBlock;
import bisq.core.dao.monitoring.model.ProposalStateBlock;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.monitor.DoubleValueItem;
import bisq.core.monitor.LongValueItem;
import bisq.core.monitor.ReportingItems;
import bisq.core.monitor.StringValueItem;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.Statistic;
import bisq.network.p2p.peers.PeerManager;
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

import java.io.IOException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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

        executor = Utilities.newCachedThreadPool(5);
        httpClient = HttpClient.newHttpClient();

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
    }

    public void shutDown() {
        if (heartBeatTimer != null) {
            heartBeatTimer.stop();
        }
        if (dataReportTimer != null) {
            dataReportTimer.stop();
        }

        Utilities.shutdownAndAwaitTermination(executor, 2, TimeUnit.SECONDS);
    }

    private void sendHeartBeat() {
        if (p2PService.getAddress() == null) {
            return;
        }
        ReportingItems reportingItems = new ReportingItems(getMyAddress());
        reportingItems.add(LongValueItem.usedMemoryInMB.withValue(Profiler.getUsedMemoryInMB()));
        sendReportingItems(reportingItems);
    }

    private void sendBlockRelatedData() {
        if (p2PService.getAddress() == null) {
            return;
        }

        ReportingItems reportingItems = new ReportingItems(getMyAddress());
        int daoStateChainHeight = daoStateService.getChainHeight();
        reportingItems.add(LongValueItem.daoStateChainHeight.withValue(daoStateChainHeight));
        daoStateService.getLastBlock().map(block -> (block.getTime() / 1000))
                .ifPresent(blockTime -> reportingItems.add(LongValueItem.blockTimeIsSec.withValue(blockTime)));
        LinkedList<DaoStateBlock> daoStateBlockChain = daoStateMonitoringService.getDaoStateBlockChain();
        if (!daoStateBlockChain.isEmpty()) {
            String daoStateHash = Utilities.bytesAsHexString(daoStateBlockChain.getLast().getMyStateHash().getHash());
            reportingItems.add(StringValueItem.daoStateHash.withValue(daoStateHash));
        }

        LinkedList<ProposalStateBlock> proposalStateBlockChain = proposalStateMonitoringService.getProposalStateBlockChain();
        if (!proposalStateBlockChain.isEmpty()) {
            String proposalHash = Utilities.bytesAsHexString(proposalStateBlockChain.getLast().getMyStateHash().getHash());
            reportingItems.add(StringValueItem.proposalHash.withValue(proposalHash));
        }

        LinkedList<BlindVoteStateBlock> blindVoteStateBlockChain = blindVoteStateMonitoringService.getBlindVoteStateBlockChain();
        if (!blindVoteStateBlockChain.isEmpty()) {
            String blindVoteHash = Utilities.bytesAsHexString(blindVoteStateBlockChain.getLast().getMyStateHash().getHash());
            reportingItems.add(StringValueItem.blindVoteHash.withValue(blindVoteHash));
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
        numItemsByType.forEach((key, value) -> reportingItems.add(LongValueItem.from(key, value)));

        // Network
        reportingItems.add(LongValueItem.numConnections.withValue(networkNode.getAllConnections().size()));
        reportingItems.add(LongValueItem.peakNumConnections.withValue(peerManager.getPeakNumConnections()));
        reportingItems.add(LongValueItem.numAllConnectionsLostEvents.withValue(peerManager.getNumAllConnectionsLostEvents()));
        reportingItems.add(LongValueItem.sentBytes.withValue(Statistic.getTotalSentBytes()));
        reportingItems.add(LongValueItem.receivedBytes.withValue(Statistic.getTotalReceivedBytes()));
        reportingItems.add(DoubleValueItem.sentBytesPerSec.withValue(Statistic.getTotalSentBytesPerSec()));
        reportingItems.add(DoubleValueItem.sentMessagesPerSec.withValue(Statistic.getNumTotalSentMessagesPerSec()));
        reportingItems.add(DoubleValueItem.receivedBytesPerSec.withValue(Statistic.getTotalReceivedBytesPerSec()));
        reportingItems.add(DoubleValueItem.receivedMessagesPerSec.withValue(Statistic.numTotalReceivedMessagesPerSec()));

        // Node
        reportingItems.add(LongValueItem.usedMemoryInMB.withValue(Profiler.getUsedMemoryInMB()));
        reportingItems.add(LongValueItem.totalMemoryInMB.withValue(Profiler.getTotalMemoryInMB()));
        reportingItems.add(LongValueItem.jvmStartTimeInSec.withValue((ManagementFactory.getRuntimeMXBean().getStartTime() / 1000)));
        reportingItems.add(LongValueItem.maxConnections.withValue(maxConnections));
        reportingItems.add(StringValueItem.version.withValue(Version.VERSION));

        // If no commit hash is found we use 0 in hex format
        String commitHash = Version.findCommitHash().orElse("00");
        reportingItems.add(StringValueItem.commitHash.withValue(commitHash));

        sendReportingItems(reportingItems);
    }

    private void sendReportingItems(ReportingItems reportingItems) {
        CompletableFuture.runAsync(() -> {
            log.info("Send report to monitor server: {}", reportingItems.toString());
            // We send the data as hex encoded protobuf data. We do not use the envelope as it is not part of the p2p system.
            byte[] protoMessageAsBytes = reportingItems.toProtoMessageAsBytes();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(seedNodeReportingServerUrl))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(protoMessageAsBytes))
                        .header("User-Agent", getMyAddress())
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.error("Response error message: {}", response);
                }
            } catch (IOException e) {
                log.warn("IOException at sending reporting. {}", e.getMessage());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private String getMyAddress() {
        return p2PService.getAddress() != null ? p2PService.getAddress().getFullAddress() : "N/A";
    }

}
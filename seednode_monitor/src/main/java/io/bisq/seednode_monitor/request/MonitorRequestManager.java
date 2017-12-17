package io.bisq.seednode_monitor.request;

import io.bisq.common.Clock;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.util.MathUtils;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.ConnectionListener;
import io.bisq.network.p2p.network.NetworkNode;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.seednode_monitor.Metrics;
import io.bisq.seednode_monitor.SeedNodeMonitorMain;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MonitorRequestManager implements ConnectionListener {
    private static final long RETRY_DELAY_SEC = 30;
    private static final long CLEANUP_TIMER = 60;
    private static final long REQUEST_PERIOD_SEC = 60 * 10;
    private static final long REQUEST_PERIOD_MIN = 30;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onDataReceived();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private P2PDataStorage dataStorage;
    private SeedNodesRepository seedNodesRepository;
    private Clock clock;
    private final Set<NodeAddress> seedNodeAddresses;
    //TODO
    private Listener listener = new Listener() {
        @Override
        public void onDataReceived() {

        }
    };

    private final Map<NodeAddress, MonitorRequestHandler> handlerMap = new HashMap<>();
    private final Map<NodeAddress, Metrics> metricsMap = new HashMap<>();
    private Map<NodeAddress, Timer> retryTimerMap = new HashMap<>();
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MonitorRequestManager(NetworkNode networkNode,
                                 P2PDataStorage dataStorage,
                                 SeedNodesRepository seedNodesRepository,
                                 Clock clock) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.seedNodesRepository = seedNodesRepository;
        this.clock = clock;

        this.networkNode.addConnectionListener(this);

        seedNodeAddresses = new HashSet<>(seedNodesRepository.getSeedNodeAddresses());
        seedNodeAddresses.addAll(seedNodesRepository.getSeedNodeAddressesOldVersions());

        seedNodeAddresses.stream().forEach(nodeAddress -> metricsMap.put(nodeAddress, new Metrics()));
    }

    public void shutDown() {
        Log.traceCall();
        stopped = true;
        stopAllRetryTimers();
        networkNode.removeConnectionListener(this);
        closeAllHandlers();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        // We want get the logs each 10 minutes
        clock.start();
        clock.addListener(new Clock.Listener() {
            @Override
            public void onSecondTick() {
                //TODO test
                processOnMinuteTick();
            }

            @Override
            public void onMinuteTick() {
                // processOnMinuteTick();
            }

            @Override
            public void onMissedSecondTick(long missed) {
            }
        });
    }

    private void processOnMinuteTick() {
       // long minutes = System.currentTimeMillis() / 1000 / 60;
        long minutes = System.currentTimeMillis() / 1000 ;
        if (minutes % REQUEST_PERIOD_MIN == 0) {
            stopAllRetryTimers();
            closeAllConnections();

            // we give 1 sec. for all connection shutdown
            final int[] delay = {1000};
            seedNodeAddresses.stream().forEach(nodeAddress -> {
                UserThread.runAfter(() -> requestData(nodeAddress), delay[0], TimeUnit.MILLISECONDS);
                delay[0] += 100;
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        Log.traceCall();
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        Log.traceCall();
        closeHandler(connection);
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RequestData
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestData(NodeAddress nodeAddress) {
        if (!stopped) {
            if (!handlerMap.containsKey(nodeAddress)) {
                MonitorRequestHandler requestDataHandler = new MonitorRequestHandler(networkNode,
                        dataStorage,
                        metricsMap.get(nodeAddress),
                        new MonitorRequestHandler.Listener() {
                            @Override
                            public void onComplete() {
                                log.trace("RequestDataHandshake of outbound connection complete. nodeAddress={}",
                                        nodeAddress);
                                stopRetryTimer(nodeAddress);

                                // need to remove before listeners are notified as they cause the update call
                                handlerMap.remove(nodeAddress);
                                listener.onDataReceived();

                                onMetricsUpdated();
                            }

                            @Override
                            public void onFault(String errorMessage) {
                                handlerMap.remove(nodeAddress);
                                onMetricsUpdated();

                                final Timer timer = UserThread.runAfter(() -> requestData(nodeAddress), RETRY_DELAY_SEC);
                                retryTimerMap.put(nodeAddress, timer);
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

    private void onMetricsUpdated() {
        Map<String, Double> accumulatedValues = new HashMap<>();
        final double[] items = {0};
        metricsMap.entrySet().stream().forEach(e -> {
            final List<Map<String, Integer>> receivedObjectsList = e.getValue().getReceivedObjectsList();
            if (!receivedObjectsList.isEmpty()) {
                items[0] += 1;
                Map<String, Integer> last = receivedObjectsList.get(receivedObjectsList.size() - 1);
                last.entrySet().stream().forEach(e2 -> {
                    int accuValue = e2.getValue();
                    if (accumulatedValues.containsKey(e2.getKey()))
                        accuValue += accumulatedValues.get(e2.getKey());

                    accumulatedValues.put(e2.getKey(), (double) accuValue);
                });
            }
        });

        Map<String, Double> averageValues = new HashMap<>();
        accumulatedValues.entrySet().stream().forEach(e -> {
            averageValues.put(e.getKey(), e.getValue() / items[0]);
        });

        StringBuilder sb = new StringBuilder("\n#################################################################\n");

        metricsMap.entrySet().stream().forEach(e -> {
            final OptionalDouble averageOptional = e.getValue().getRequestDurations().stream().mapToLong(value -> value).average();
            int average = 0;
            if (averageOptional.isPresent())
                average = (int) averageOptional.getAsDouble();
            sb.append("\nNode: ")
                    .append(e.getKey())
                    .append(" (")
                    .append(seedNodesRepository.getOperator(e.getKey()))
                    .append(")\n")
                    .append("Durations: ")
                    .append(e.getValue().getRequestDurations())
                    .append("\n")
                    .append("Duration average: ")
                    .append(average)
                    .append("\n")
                    .append("Errors: ")
                    .append(e.getValue().getErrorMessages())
                    .append("\n")
                    .append("All data: ")
                    .append(e.getValue().getReceivedObjectsList())
                    .append("\n");

            final List<Map<String, Integer>> receivedObjectsList = e.getValue().getReceivedObjectsList();
            if (!receivedObjectsList.isEmpty()) {
                Map<String, Integer> last = receivedObjectsList.get(receivedObjectsList.size() - 1);
                sb.append("Last data: ").append(last).append("\nAverage of last:\n");
                last.entrySet().stream().forEach(e2 -> {
                    double deviation = MathUtils.roundDouble((double) e2.getValue() / averageValues.get(e2.getKey()) * 100, 2);
                    sb.append(e2.getKey()).append(": ")
                            .append(deviation).append(" % compared to average")
                            .append("\n");
                });
            }
        });
        sb.append("\n#################################################################\n\n");
        log.info(sb.toString());
        SeedNodeMonitorMain.metricsLog = sb.toString();
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

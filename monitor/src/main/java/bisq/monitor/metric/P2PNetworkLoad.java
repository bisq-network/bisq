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

package bisq.monitor.metric;

import bisq.monitor.AvailableTor;
import bisq.monitor.Metric;
import bisq.monitor.Monitor;
import bisq.monitor.Reporter;
import bisq.monitor.ThreadGate;

import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.SetupListener;
import bisq.network.p2p.network.TorNetworkNode;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.keepalive.KeepAliveManager;
import bisq.network.p2p.peers.peerexchange.PeerExchangeManager;
import bisq.network.p2p.storage.messages.BroadcastMessage;

import bisq.common.ClockWatcher;
import bisq.common.config.Config;
import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;

import java.time.Clock;

import java.io.File;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Contacts a list of hosts and asks them for all the data we do not have. The
 * answers are then compiled into buckets of message types. Based on these
 * buckets, the Metric reports (for each host) the message types observed and
 * their number along with a relative comparison between all hosts.
 *
 * @author Florian Reimair
 *
 */
@Slf4j
public class P2PNetworkLoad extends Metric implements MessageListener, SetupListener {

    private static final String TOR_PROXY_PORT = "run.torProxyPort";
    private static final String MAX_CONNECTIONS = "run.maxConnections";
    private static final String HISTORY_SIZE = "run.historySize";
    private NetworkNode networkNode;
    private final File torHiddenServiceDir = new File("metric_" + getName());
    private final ThreadGate hsReady = new ThreadGate();
    private final Map<String, Counter> buckets = new ConcurrentHashMap<>();

    /**
     * Buffers the last X message we received. New messages will only be logged in case
     * the message isn't already in the history. Note that the oldest message hashes are
     * dropped to record newer hashes.
     */
    private Map<Integer, Object> history;
    private long lastRun = 0;

    /**
     * History implementation using a {@link LinkedHashMap} and its
     * {@link LinkedHashMap#removeEldestEntry(Map.Entry)} option.
     */
    private static class FixedSizeHistoryTracker<K, V> extends LinkedHashMap<K, V> {
        final int historySize;

        FixedSizeHistoryTracker(int historySize) {
            super(historySize, 10, true);
            this.historySize = historySize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > historySize;
        }
    }

    @Override
    protected void execute() {

        // in case we do not have a NetworkNode up and running, we create one
        if (null == networkNode) {
            // prepare the gate
            hsReady.engage();

            // start the network node
            networkNode = new TorNetworkNode(Integer.parseInt(configuration.getProperty(TOR_PROXY_PORT, "9053")),
                    new CoreNetworkProtoResolver(Clock.systemDefaultZone()), false,
                    new AvailableTor(Monitor.TOR_WORKING_DIR, torHiddenServiceDir.getName()), null);
            networkNode.start(this);

            // wait for the HS to be published
            hsReady.await();

            // boot up P2P node
            try {
                Config config = new Config();
                CorruptedStorageFileHandler corruptedStorageFileHandler = new CorruptedStorageFileHandler();
                int maxConnections = Integer.parseInt(configuration.getProperty(MAX_CONNECTIONS, "12"));
                NetworkProtoResolver networkProtoResolver = new CoreNetworkProtoResolver(Clock.systemDefaultZone());
                CorePersistenceProtoResolver persistenceProtoResolver = new CorePersistenceProtoResolver(null,
                        networkProtoResolver);
                DefaultSeedNodeRepository seedNodeRepository = new DefaultSeedNodeRepository(config);
                PeerManager peerManager = new PeerManager(networkNode, seedNodeRepository, new ClockWatcher(),
                        new PersistenceManager<>(torHiddenServiceDir, persistenceProtoResolver, corruptedStorageFileHandler), maxConnections);

                // init file storage
                peerManager.readPersisted(() -> {
                });

                PeerExchangeManager peerExchangeManager = new PeerExchangeManager(networkNode, seedNodeRepository,
                        peerManager);
                // updates the peer list every now and then as well
                peerExchangeManager
                        .requestReportedPeersFromSeedNodes(seedNodeRepository.getSeedNodeAddresses().iterator().next());

                KeepAliveManager keepAliveManager = new KeepAliveManager(networkNode, peerManager);
                keepAliveManager.start();

                networkNode.addMessageListener(this);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        // report
        Map<String, String> report = new HashMap<>();

        if (lastRun != 0 && System.currentTimeMillis() - lastRun != 0) {
            // - normalize to data/minute
            double perMinuteFactor = 60000.0 / (System.currentTimeMillis() - lastRun);


            // - get snapshot so we do not loose data
            Set<String> keys = new HashSet<>(buckets.keySet());

            // - transfer values to report
            keys.forEach(key -> {
                int value = buckets.get(key).getAndReset();
                if (value != 0) {
                    report.put(key, String.format("%.2f", value * perMinuteFactor));
                }
            });

            // - report
            reporter.report(report, getName());
        }

        // - reset last run
        lastRun = System.currentTimeMillis();
    }

    public P2PNetworkLoad(Reporter reporter) {
        super(reporter);
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        history = Collections.synchronizedMap(new FixedSizeHistoryTracker<>(Integer.parseInt(configuration.getProperty(HISTORY_SIZE, "200"))));
    }

    /**
     * Efficient way to count message occurrences.
     */
    private static class Counter {
        private int value = 1;

        synchronized int getAndReset() {
            try {
                return value;
            } finally {
                value = 0;
            }
        }

        synchronized void increment() {
            value++;
        }
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof BroadcastMessage) {
            try {
                if (history.get(networkEnvelope.hashCode()) == null) {
                    history.put(networkEnvelope.hashCode(), null);
                    buckets.get(networkEnvelope.getClass().getSimpleName()).increment();
                }
            } catch (NullPointerException e) {
                // use exception handling because we hardly ever need to add a fresh bucket
                buckets.put(networkEnvelope.getClass().getSimpleName(), new Counter());
            }
        }
    }

    @Override
    public void onTorNodeReady() {
    }

    @Override
    public void onHiddenServicePublished() {
        // open the gate
        hsReady.proceed();
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
    }

    @Override
    public void onRequestCustomBridges() {
    }
}

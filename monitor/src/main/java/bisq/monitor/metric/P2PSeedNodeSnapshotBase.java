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

package bisq.monitor.metric;

import bisq.monitor.AvailableTor;
import bisq.monitor.Metric;
import bisq.monitor.Monitor;
import bisq.monitor.OnionParser;
import bisq.monitor.Reporter;
import bisq.monitor.ThreadGate;

import bisq.core.account.witness.AccountAgeWitnessStore;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.trade.statistics.TradeStatistics3Store;

import bisq.network.p2p.CloseConnectionMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.TorNetworkNode;

import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.NetworkEnvelope;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.time.Clock;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Contacts a list of hosts and asks them for all the data excluding persisted messages. The
 * answers are then compiled into buckets of message types. Based on these
 * buckets, the Metric reports (for each host) the message types observed and
 * their number.
 *
 * @author Florian Reimair
 *
 */
@Slf4j
public abstract class P2PSeedNodeSnapshotBase extends Metric implements MessageListener {

    private static final String HOSTS = "run.hosts";
    private static final String TOR_PROXY_PORT = "run.torProxyPort";
    private static final String DATABASE_DIR = "run.dbDir";
    final Map<NodeAddress, Statistics<?>> bucketsPerHost = new ConcurrentHashMap<>();
    private final ThreadGate gate = new ThreadGate();
    protected final Set<byte[]> hashes = new TreeSet<>(Arrays::compare);

    /**
     * Statistics Interface for use with derived classes.
     *
     * @param <T> the value type of the statistics implementation
     */
    protected abstract static class Statistics<T> {
        protected final Map<String, T> buckets = new HashMap<>();

        abstract void log(Object message);

        Map<String, T> values() {
            return buckets;
        }

        void reset() {
            buckets.clear();
        }
    }

    public P2PSeedNodeSnapshotBase(Reporter reporter) {
        super(reporter);
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        if (hashes.isEmpty() && configuration.getProperty(DATABASE_DIR) != null) {
            File dir = new File(configuration.getProperty(DATABASE_DIR));
            String networkPostfix = "_" + BaseCurrencyNetwork.values()[Version.getBaseCurrencyNetwork()].toString();
            try {
                CorePersistenceProtoResolver persistenceProtoResolver = new CorePersistenceProtoResolver(null, null);

                //TODO will not work with historical data... should be refactored to re-use code for reading resource files
                TradeStatistics3Store tradeStatistics3Store = new TradeStatistics3Store();
                PersistenceManager<TradeStatistics3Store> tradeStatistics3PersistenceManager = new PersistenceManager<>(dir,
                        persistenceProtoResolver, null);
                tradeStatistics3PersistenceManager.initialize(tradeStatistics3Store,
                        tradeStatistics3Store.getDefaultStorageFileName() + networkPostfix,
                        PersistenceManager.Source.NETWORK);
                TradeStatistics3Store persistedTradeStatistics3Store = tradeStatistics3PersistenceManager.getPersisted();
                if (persistedTradeStatistics3Store != null) {
                    tradeStatistics3Store.getMap().putAll(persistedTradeStatistics3Store.getMap());
                }
                hashes.addAll(tradeStatistics3Store.getMap().keySet().stream()
                        .map(byteArray -> byteArray.bytes).collect(Collectors.toSet()));

                AccountAgeWitnessStore accountAgeWitnessStore = new AccountAgeWitnessStore();
                PersistenceManager<AccountAgeWitnessStore> accountAgeWitnessPersistenceManager = new PersistenceManager<>(dir,
                        persistenceProtoResolver, null);
                accountAgeWitnessPersistenceManager.initialize(accountAgeWitnessStore,
                        accountAgeWitnessStore.getDefaultStorageFileName() + networkPostfix,
                        PersistenceManager.Source.NETWORK);
                AccountAgeWitnessStore persistedAccountAgeWitnessStore = accountAgeWitnessPersistenceManager.getPersisted();
                if (persistedAccountAgeWitnessStore != null) {
                    accountAgeWitnessStore.getMap().putAll(persistedAccountAgeWitnessStore.getMap());
                }
                hashes.addAll(accountAgeWitnessStore.getMap().keySet().stream()
                        .map(byteArray -> byteArray.bytes).collect(Collectors.toSet()));
            } catch (NullPointerException e) {
                // in case there is no store file
                log.error("There is no storage file where there should be one: {}", dir.getAbsolutePath());
            }
        }
    }

    @Override
    protected void execute() {
        // start the network node
        final NetworkNode networkNode = new TorNetworkNode(Integer.parseInt(configuration.getProperty(TOR_PROXY_PORT, "9054")),
                new CoreNetworkProtoResolver(Clock.systemDefaultZone()), false,
                new AvailableTor(Monitor.TOR_WORKING_DIR, "unused"), null);
        // we do not need to start the networkNode, as we do not need the HS
        //networkNode.start(this);

        // clear our buckets
        bucketsPerHost.clear();

        getRequests().forEach(getDataRequest -> send(networkNode, getDataRequest));

        report();
    }

    protected abstract List<NetworkEnvelope> getRequests();

    protected void send(NetworkNode networkNode, NetworkEnvelope message) {

        ArrayList<Thread> threadList = new ArrayList<>();

        // for each configured host
        for (String current : configuration.getProperty(HOSTS, "").split(",")) {
            threadList.add(new Thread(() -> {

                try {
                    // parse Url
                    NodeAddress target = OnionParser.getNodeAddress(current);

                    // do the data request
                    aboutToSend(message);
                    SettableFuture<Connection> future = networkNode.sendMessage(target, message);

                    Futures.addCallback(future, new FutureCallback<>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            connection.addMessageListener(P2PSeedNodeSnapshotBase.this);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            gate.proceed();
                            log.error(
                                    "Sending {} failed. That is expected if the peer is offline.\n\tException={}", message.getClass().getSimpleName(), throwable.getMessage());
                        }
                    }, MoreExecutors.directExecutor());

                } catch (Exception e) {
                    gate.proceed(); // release the gate on error
                    e.printStackTrace();
                }
            }, current));
        }

        gate.engage(threadList.size());

        // start all threads and wait until they all finished. We do that so we can
        // minimize the time between querying the hosts and therefore the chance of
        // inconsistencies.
        threadList.forEach(Thread::start);

        gate.await();
    }

    protected void aboutToSend(NetworkEnvelope message) {
    }

    /**
     * Report all the stuff. Uses the configured reporter directly.
     */
    abstract void report();

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (treatMessage(networkEnvelope, connection)) {
            gate.proceed();
        } else if (networkEnvelope instanceof CloseConnectionMessage) {
            gate.unlock();
        } else {
            log.warn("Got an unexpected message of type <{}>",
                    networkEnvelope.getClass().getSimpleName());
        }
        connection.removeMessageListener(this);
    }

    protected abstract boolean treatMessage(NetworkEnvelope networkEnvelope, Connection connection);
}

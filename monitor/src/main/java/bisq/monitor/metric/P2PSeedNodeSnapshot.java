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

import bisq.core.proto.network.CoreNetworkProtoResolver;

import bisq.network.p2p.CloseConnectionMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.TorNetworkNode;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.network.NetworkEnvelope;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.net.MalformedURLException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkNotNull;

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
public class P2PSeedNodeSnapshot extends Metric implements MessageListener {

    private static final String HOSTS = "run.hosts";
    private static final String TOR_PROXY_PORT = "run.torProxyPort";
    Statistics statistics;
    final Map<NodeAddress, Statistics> bucketsPerHost = new ConcurrentHashMap<>();
    private final Set<byte[]> hashes = new TreeSet<>(Arrays::compare);
    private final ThreadGate gate = new ThreadGate();

    /**
     * Statistics Interface for use with derived classes.
     *
     * @param <T> the value type of the statistics implementation
     */
    protected interface Statistics<T> {

        Statistics create();

        void log(ProtectedStoragePayload message);

        Map<String, T> values();

        void reset();
    }

    /**
     * Efficient way to count message occurrences.
     */
    private class Counter {
        private int value = 0;

        int value() {
            return value;
        }

        void increment() {
            value++;
        }
    }

    /**
     * Use a counter to do statistics.
     */
    private class MyStatistics  implements  Statistics<Counter> {

        private final Map<String, Counter> buckets = new HashMap<>();

        @Override
        public Statistics create() {
            return new MyStatistics();
        }

        @Override
        public synchronized void log(ProtectedStoragePayload message) {

            // For logging different data types
            String className = message.getClass().getSimpleName();

            buckets.putIfAbsent(className, new Counter());
            buckets.get(className).increment();
        }

        @Override
        public Map<String, Counter> values() {
            return buckets;
        }

        @Override
        public synchronized void reset() {
            buckets.clear();
        }
    }

    public P2PSeedNodeSnapshot(Reporter reporter) {
        super(reporter);

        statistics = new MyStatistics();
    }

    @Override
    protected void execute() {
        // start the network node
        final NetworkNode networkNode = new TorNetworkNode(Integer.parseInt(configuration.getProperty(TOR_PROXY_PORT, "9054")),
                new CoreNetworkProtoResolver(), false,
                new AvailableTor(Monitor.TOR_WORKING_DIR, "unused"));
        // we do not need to start the networkNode, as we do not need the HS
        //networkNode.start(this);

        // clear our buckets
        bucketsPerHost.clear();
        ArrayList<Thread> threadList = new ArrayList<>();

        // for each configured host
        for (String current : configuration.getProperty(HOSTS, "").split(",")) {
            threadList.add(new Thread(() -> {

                    try {
                        // parse Url
                        NodeAddress target = OnionParser.getNodeAddress(current);

                        // do the data request
                        SettableFuture<Connection> future = networkNode.sendMessage(target,
                                new PreliminaryGetDataRequest(new Random().nextInt(), hashes));

                        Futures.addCallback(future, new FutureCallback<>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                connection.addMessageListener(P2PSeedNodeSnapshot.this);
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                gate.proceed();
                                log.error(
                                        "Sending PreliminaryDataRequest failed. That is expected if the peer is offline.\n\tException="
                                                + throwable.getMessage());
                            }
                        });

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

        report();
    }

    /**
     * Report all the stuff. Uses the configured reporter directly.
     */
    void report() {

        // report
        Map<String, String> report = new HashMap<>();
        // - assemble histograms
        bucketsPerHost.forEach((host, statistics) -> statistics.values().forEach((type, counter) -> report
                .put(OnionParser.prettyPrint(host) + ".numberOfMessages." + type, String.valueOf(((Counter) counter).value()))));

        // - assemble diffs
        //   - transfer values
        Map<String, Statistics> messagesPerHost = new HashMap<>();
        bucketsPerHost.forEach((host, value) -> messagesPerHost.put(OnionParser.prettyPrint(host), value));

        //   - pick reference seed node and its values
        Optional<String> referenceHost = messagesPerHost.keySet().stream().sorted().findFirst();
        Map<String, Counter> referenceValues = messagesPerHost.get(referenceHost.get()).values();

        //   - calculate diffs
        messagesPerHost.forEach(
            (host, statistics) -> {
                statistics.values().forEach((messageType, count) -> {
                    try {
                        report.put(OnionParser.prettyPrint(host) + ".relativeNumberOfMessages." + messageType,
                                String.valueOf(((Counter) count).value() - referenceValues.get(messageType).value()));
                    } catch (MalformedURLException ignore) {
                        log.error("we should never got here");
                    }
                });
                try {
                    report.put(OnionParser.prettyPrint(host) + ".referenceHost", referenceHost.get());
                } catch (MalformedURLException ignore) {
                    log.error("we should never got here");
                }
            });

        // cleanup for next run
        bucketsPerHost.forEach((host, statistics) -> statistics.reset());

        // when our hash cache exceeds a hard limit, we clear the cache and start anew
        if (hashes.size() > 150000)
            hashes.clear();

        reporter.report(report, getName());
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetDataResponse) {

            Statistics result = this.statistics.create();

            GetDataResponse dataResponse = (GetDataResponse) networkEnvelope;
            final Set<ProtectedStorageEntry> dataSet = dataResponse.getDataSet();
            dataSet.forEach(e -> {
                final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                if (protectedStoragePayload == null) {
                    log.warn("StoragePayload was null: {}", networkEnvelope.toString());
                    return;
                }

                result.log(protectedStoragePayload);
            });

            Set<PersistableNetworkPayload> persistableNetworkPayloadSet = dataResponse
                    .getPersistableNetworkPayloadSet();
            if (persistableNetworkPayloadSet != null) {
                persistableNetworkPayloadSet.forEach(persistableNetworkPayload -> {

                    // memorize message hashes
                    //Byte[] bytes = new Byte[persistableNetworkPayload.getHash().length];
                    //Arrays.setAll(bytes, n -> persistableNetworkPayload.getHash()[n]);

                    //hashes.add(bytes);

                    hashes.add(persistableNetworkPayload.getHash());
                });
            }

            checkNotNull(connection.getPeersNodeAddressProperty(),
                    "although the property is nullable, we need it to not be null");
            bucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), result);

            connection.shutDown(CloseConnectionReason.APP_SHUT_DOWN);
            gate.proceed();
        } else if (networkEnvelope instanceof CloseConnectionMessage) {
            gate.unlock();
        } else {
            log.warn("Got a message of type <{}>, expected <GetDataResponse>",
                    networkEnvelope.getClass().getSimpleName());
        }
    }
}

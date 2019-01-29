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

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.monitor.AvailableTor;
import bisq.monitor.Metric;
import bisq.monitor.Monitor;
import bisq.monitor.OnionParser;
import bisq.monitor.Reporter;
import bisq.monitor.ThreadGate;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.SetupListener;
import bisq.network.p2p.network.TorNetworkNode;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
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

    private static final String HOSTS = "run.hosts";
    private static final String TOR_PROXY_PORT = "run.torProxyPort";
    private NetworkNode networkNode;
    private final File torHiddenServiceDir = new File("metric_p2pNetworkLoad");
    private int nonce;
    private Map<NodeAddress, Map<String, Counter>> bucketsPerHost = new ConcurrentHashMap<>();
    private Set<byte[]> hashes = new HashSet<>();
    private boolean reportFindings;
    private final ThreadGate hsReady = new ThreadGate();
    private final ThreadGate gate = new ThreadGate();

    /**
     * Efficient way to count message occurrences.
     */
    private class Counter {
        private int value = 1;

        public int value() {
            return value;
        }

        public void increment() {
            value++;
        }
    }

    public P2PNetworkLoad(Reporter reporter) {
        super(reporter);

        Version.setBaseCryptoNetworkId(0); // set to BTC_MAINNET
    }

    @Override
    protected void execute() {
        // in case we do not have a NetworkNode up and running, we create one
        if (null == networkNode) {
            // prepare the gate
            hsReady.engage();

            // start the network node
            networkNode = new TorNetworkNode(Integer.parseInt(configuration.getProperty(TOR_PROXY_PORT, "9053")),
                    new CoreNetworkProtoResolver(), false,
                    new AvailableTor(Monitor.TOR_WORKING_DIR, torHiddenServiceDir.getName()));
            networkNode.start(this);

            // wait for the HS to be published
            hsReady.await();
        }

        // clear our buckets
        bucketsPerHost.clear();
        ArrayList<Thread> threadList = new ArrayList<>();

        // in case we just started anew, do not report our findings as they contain not
        // only the changes since our last run, but a whole lot more dating back even
        // till the beginning of bisq.
        if (hashes.isEmpty())
            reportFindings = false;
        else
            reportFindings = true;

        // for each configured host
        for (String current : configuration.getProperty(HOSTS, "").split(",")) {
            threadList.add(new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        // parse Url
                        NodeAddress target = OnionParser.getNodeAddress(current);

                        // do the data request
                        nonce = new Random().nextInt();
                        SettableFuture<Connection> future = networkNode.sendMessage(target,
                                new PreliminaryGetDataRequest(nonce, hashes));

                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                connection.addMessageListener(P2PNetworkLoad.this);
                                log.debug("Send PreliminaryDataRequest to " + connection + " succeeded.");
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.error(
                                        "Sending PreliminaryDataRequest failed. That is expected if the peer is offline.\n\tException="
                                                + throwable.getMessage());
                            }
                        });

                    } catch (Exception e) {
                        gate.proceed(); // release the gate on error
                        e.printStackTrace();
                    }

                }
            }, current));
        }

        gate.engage(threadList.size());

        // start all threads and wait until they all finished. We do that so we can
        // minimize the time between querying the hosts and therefore the chance of
        // inconsistencies.
        threadList.forEach(Thread::start);
        gate.await();

        // report
        Map<String, String> report = new HashMap<>();
        // - assemble histograms
        bucketsPerHost.forEach((host, buckets) -> buckets.forEach((type, counter) -> report
                .put(OnionParser.prettyPrint(host) + "." + type, String.valueOf(counter.value()))));

        // - assemble diffs
        Map<String, Integer> messagesPerHost = new HashMap<>();
        bucketsPerHost.forEach((host, buckets) -> messagesPerHost.put(OnionParser.prettyPrint(host),
                buckets.values().stream().collect(Collectors.summingInt(Counter::value))));
        Optional<String> referenceHost = messagesPerHost.keySet().stream().sorted().findFirst();
        Integer referenceValue = messagesPerHost.get(referenceHost.get());

        messagesPerHost.forEach(
                (host, numberOfMessages) -> {
                    try {
                        report.put(OnionParser.prettyPrint(host) + ".relativeNumberOfMessages",
                                String.valueOf(numberOfMessages - referenceValue));
                        report.put(OnionParser.prettyPrint(host) + ".referenceHost", referenceHost.get());
                        report.put(OnionParser.prettyPrint(host) + ".referenceValue", String.valueOf(referenceValue));
                    } catch (MalformedURLException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                });

        // when our hash cache exceeds a hard limit, we clear the cache and start anew
        if (hashes.size() > 150000)
            hashes.clear();

        // report our findings iff we have not just started anew
        if (reportFindings)
            reporter.report(report, "bisq." + getName());
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetDataResponse) {


            GetDataResponse dataResponse = (GetDataResponse) networkEnvelope;
            Map<String, Counter> buckets = new HashMap<>();
            final Set<ProtectedStorageEntry> dataSet = dataResponse.getDataSet();
            dataSet.stream().forEach(e -> {
                final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                if (protectedStoragePayload == null) {
                    log.warn("StoragePayload was null: {}", networkEnvelope.toString());
                    return;
                }

                // memorize message hashes
                hashes.add(P2PDataStorage.get32ByteHash(protectedStoragePayload));

                // For logging different data types
                String className = protectedStoragePayload.getClass().getSimpleName();
                try {
                    buckets.get(className).increment();
                } catch (NullPointerException nullPointerException) {
                    buckets.put(className, new Counter());
                }
            });

            Set<PersistableNetworkPayload> persistableNetworkPayloadSet = dataResponse
                    .getPersistableNetworkPayloadSet();
            if (persistableNetworkPayloadSet != null) {
                persistableNetworkPayloadSet.stream().forEach(persistableNetworkPayload -> {

                    // memorize message hashes
                    hashes.add(persistableNetworkPayload.getHash());

                    // For logging different data types
                    String className = persistableNetworkPayload.getClass().getSimpleName();
                    try {
                        buckets.get(className).increment();
                    } catch (NullPointerException nullPointerException) {
                        buckets.put(className, new Counter());
                    }
                });
            }

            bucketsPerHost.put(connection.peersNodeAddressProperty().getValue(), buckets);

            connection.removeMessageListener(this);
            gate.proceed();
        }
    }

    @Override
    public void onTorNodeReady() {
        // TODO Auto-generated method stub
    }

    @Override
    public void onHiddenServicePublished() {
        // open the gate
        hsReady.proceed();
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onRequestCustomBridges() {
        // TODO Auto-generated method stub

    }
}

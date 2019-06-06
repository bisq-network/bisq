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

import bisq.monitor.OnionParser;
import bisq.monitor.Reporter;

import bisq.core.dao.monitoring.model.StateHash;
import bisq.core.dao.monitoring.network.messages.GetBlindVoteStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetProposalStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetStateHashesResponse;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.network.NetworkEnvelope;

import java.net.MalformedURLException;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Contacts a list of hosts and asks them for all the data excluding persisted messages. The
 * answers are then compiled into buckets of message types. Based on these
 * buckets, the Metric reports (for each host) the message types observed and
 * their number.
 *
 * Furthermore, since the DAO is a thing now, the consistency of the DAO state held by each host is assessed and reported.
 *
 * @author Florian Reimair
 *
 */
@Slf4j
public class P2PSeedNodeSnapshot extends P2PSeedNodeSnapshotBase {

    Statistics statistics;
    final Map<NodeAddress, Statistics> bucketsPerHost = new ConcurrentHashMap<>();
    protected final Set<byte[]> hashes = new TreeSet<>(Arrays::compare);
    private int daostateheight = 550000;
    private int proposalheight = daostateheight;
    private int blindvoteheight = daostateheight;

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
        public synchronized void log(Object message) {

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


//        AppendOnlyDataStoreService appendOnlyDataStoreService,
//        ProtectedDataStoreService protectedDataStoreService,
//        ResourceDataStoreService resourceDataStoreService,
//        Storage<SequenceNumberMap> sequenceNumberMapStorage) {
//
//        Set<byte[]> excludedKeys = dataStorage.getAppendOnlyDataStoreMap().keySet().stream()
//                .map(e -> e.bytes)
//                .collect(Collectors.toSet());
//
//        Set<byte[]> excludedKeysFromPersistedEntryMap = dataStorage.getProtectedDataStoreMap().keySet()
//                .stream()
//                .map(e -> e.bytes)
//                .collect(Collectors.toSet());

        statistics = new MyStatistics();
    }

    protected List<NetworkEnvelope> getRequests() {
        List<NetworkEnvelope> result = new ArrayList<>();

        Random random = new Random();
        result.add(new PreliminaryGetDataRequest(random.nextInt(), hashes));

        result.add(new GetDaoStateHashesRequest(daostateheight, random.nextInt()));

        result.add(new GetProposalStateHashesRequest(proposalheight, random.nextInt()));

        result.add(new GetBlindVoteStateHashesRequest(blindvoteheight, random.nextInt()));

        return result;
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

        //   - report
        reporter.report(report, getName());

        // - assemble dao report
        Map<String, String> daoreport = new HashMap<>();

        //   - transcode
        Map<String, Map<NodeAddress, Tuple>> perType = new HashMap<>();
        daoData.forEach((nodeAddress, daostatistics) -> daostatistics.values().forEach((type, tuple) -> {
            perType.putIfAbsent((String) type, new HashMap<>());
            perType.get(type).put(nodeAddress, (Tuple) tuple);
        }));

        //   - process dao data
        perType.forEach((type, nodeAddressTupleMap) -> {
            //   - find head
            int head = (int) nodeAddressTupleMap.values().stream().sorted((o1, o2) -> Long.compare(o1.height, o2.height)).findFirst().get().height;

            //   - update queried height
            if(type.contains("DaoState"))
                daostateheight = head - 20;
            else if(type.contains("Proposal"))
                proposalheight = head - 20;
            else
                blindvoteheight = head - 20;

            //   - calculate diffs
            nodeAddressTupleMap.forEach((nodeAddress, tuple) -> daoreport.put(type + "." + OnionParser.prettyPrint(nodeAddress) + ".head", Long.toString(tuple.height - head)));

            //   - memorize hashes
            Set<ByteBuffer> states = new HashSet<>();
            nodeAddressTupleMap.forEach((nodeAddress, tuple) -> states.add(ByteBuffer.wrap(tuple.hash)));
            nodeAddressTupleMap.forEach((nodeAddress, tuple) -> daoreport.put(type + "." + OnionParser.prettyPrint(nodeAddress) + ".hash", Integer.toString(Arrays.asList(states.toArray()).indexOf(ByteBuffer.wrap(tuple.hash)))));
        });

        daoData.clear();

        //   - report
        reporter.report(daoreport, "DaoStateSnapshot");
    }

    private class Tuple {
        private final long height;
        private final byte[] hash;

        Tuple(long height, byte[] hash) {
            this.height = height;
            this.hash = hash;
        }
    }

    private class DaoStatistics implements Statistics<Tuple> {

        Map<String, Tuple> buckets = new ConcurrentHashMap<>();

        @Override
        public Statistics create() {
            return new DaoStatistics();
        }

        @Override
        public void log(Object message) {
            // get last entry
            StateHash last = (StateHash) ((GetStateHashesResponse) message).getStateHashes().get(((GetStateHashesResponse) message).getStateHashes().size() - 1);

            // For logging different data types
            String className = last.getClass().getSimpleName();

            buckets.putIfAbsent(className, new Tuple(last.getHeight(), last.getHash()));
        }

        @Override
        public Map<String, Tuple> values() {
            return buckets;
        }

        @Override
        public void reset() {
            buckets.clear();
        }
    }

    private Map<NodeAddress, Statistics> daoData = new ConcurrentHashMap<>();

    protected boolean treatMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        checkNotNull(connection.getPeersNodeAddressProperty(),
                "although the property is nullable, we need it to not be null");
        
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

            bucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), result);
            return true;
        } else if (networkEnvelope instanceof GetStateHashesResponse) {
            daoData.putIfAbsent(connection.getPeersNodeAddressProperty().getValue(), new DaoStatistics());

            daoData.get(connection.getPeersNodeAddressProperty().getValue()).log(networkEnvelope);

            return true;
        }
        return false;
    }
}

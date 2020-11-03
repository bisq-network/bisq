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
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.network.NetworkEnvelope;

import java.net.MalformedURLException;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
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

    final Map<NodeAddress, Statistics<Set<Integer>>> bucketsPerHost = new ConcurrentHashMap<>();
    private int daostateheight = 594000;
    private int proposalheight = daostateheight;
    private int blindvoteheight = daostateheight;

    /**
     * Use a counter to do statistics.
     */
    private static class MyStatistics extends Statistics<Set<Integer>> {

        @Override
        public synchronized void log(Object message) {

            // For logging different data types
            String className = message.getClass().getSimpleName();

            buckets.putIfAbsent(className, new HashSet<>());
            buckets.get(className).add(message.hashCode());
        }
    }

    public P2PSeedNodeSnapshot(Reporter reporter) {
        super(reporter);
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
        bucketsPerHost.forEach((host, statistics) -> statistics.values().forEach((type, set) -> report
                .put(OnionParser.prettyPrint(host) + ".numberOfMessages." + type, Integer.toString(set.size()))));

        // - assemble diffs
        //   - transfer values
        Map<String, Statistics<Set<Integer>>> messagesPerHost = new HashMap<>();
        bucketsPerHost.forEach((host, value) -> messagesPerHost.put(OnionParser.prettyPrint(host), value));

        //   - pick reference seed node and its values
        String referenceHost = "overall_number_of_unique_messages";
        Map<String, Set<Object>> referenceValues = new HashMap<>();
        messagesPerHost.forEach((host, statistics) -> statistics.values().forEach((type, set) -> {
            referenceValues.putIfAbsent(type, new HashSet<>());
            referenceValues.get(type).addAll(set);
        }));

        //   - calculate diffs
        messagesPerHost.forEach(
                (host, statistics) -> {
                    statistics.values().forEach((messageType, set) -> {
                        try {
                            report.put(OnionParser.prettyPrint(host) + ".relativeNumberOfMessages." + messageType,
                                    String.valueOf(set.size() - referenceValues.get(messageType).size()));
                        } catch (MalformedURLException | NullPointerException e) {
                            log.error("we should never have gotten here", e);
                        }
                    });
                    try {
                        report.put(OnionParser.prettyPrint(host) + ".referenceHost", referenceHost);
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
            perType.putIfAbsent(type, new HashMap<>());
            perType.get(type).put(nodeAddress, tuple);
        }));

        //   - process dao data
        perType.forEach((type, nodeAddressTupleMap) -> {
            //   - find head
            int head = nodeAddressTupleMap.values().stream().max(Comparator.comparingLong(Tuple::getHeight))
                    .map(value -> (int) value.height)
                    .orElse(0);
            int oldest = nodeAddressTupleMap.values().stream().min(Comparator.comparingLong(Tuple::getHeight))
                    .map(value -> (int) value.height)
                    .orElse(0);

            //   - update queried height
            if (type.contains("DaoState"))
                daostateheight = oldest - 20;
            else if (type.contains("Proposal"))
                proposalheight = oldest - 20;
            else
                blindvoteheight = oldest - 20;

            //   - calculate diffs
            nodeAddressTupleMap.forEach((nodeAddress, tuple) -> daoreport.put(type + "." + OnionParser.prettyPrint(nodeAddress) + ".head", Long.toString(tuple.height - head)));

            //   - memorize hashes
            Map<ByteBuffer, Integer> hitcount = new HashMap<>();
            nodeAddressTupleMap.forEach((nodeAddress, tuple) -> {
                ByteBuffer hash = ByteBuffer.wrap(tuple.hash);
                if (hitcount.containsKey(hash)) {
                    hitcount.put(hash, hitcount.get(hash) + 1);
                } else
                    hitcount.put(hash, 1);
            });

            hitcount.clear();

            nodeAddressTupleMap.forEach((nodeAddress, tuple) ->
                    daoreport.put(type + "." + OnionParser.prettyPrint(nodeAddress) + ".hash",
                            Integer.toString(Arrays.asList(hitcount.entrySet().stream()
                                    .sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue()))
                                    .map(Map.Entry::getKey).toArray()).indexOf(ByteBuffer
                                    .wrap(tuple.hash)))));

            //   - report reference head
            daoreport.put(type + ".referenceHead", Integer.toString(head));
        });

        daoData.clear();

        //   - report
        reporter.report(daoreport, "DaoStateSnapshot");
    }

    private static class Tuple {
        @Getter
        private final long height;
        private final byte[] hash;

        Tuple(long height, byte[] hash) {
            this.height = height;
            this.hash = hash;
        }
    }

    private class DaoStatistics extends Statistics<Tuple> {

        @Override
        public void log(Object message) {
            // get last entry
            StateHash last = (StateHash) ((GetStateHashesResponse) message).getStateHashes().get(((GetStateHashesResponse) message).getStateHashes().size() - 1);

            // For logging different data types
            String className = last.getClass().getSimpleName();

            buckets.putIfAbsent(className, new Tuple(last.getHeight(), last.getHash()));
        }
    }

    private final Map<NodeAddress, Statistics<Tuple>> daoData = new ConcurrentHashMap<>();

    protected boolean treatMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        checkNotNull(connection.getPeersNodeAddressProperty(),
                "although the property is nullable, we need it to not be null");

        if (networkEnvelope instanceof GetDataResponse) {

            Statistics result = new MyStatistics();

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

            dataResponse.getPersistableNetworkPayloadSet().forEach(persistableNetworkPayload -> {
                // memorize message hashes
                //Byte[] bytes = new Byte[persistableNetworkPayload.getHash().length];
                //Arrays.setAll(bytes, n -> persistableNetworkPayload.getHash()[n]);

                //hashes.add(bytes);

                hashes.add(persistableNetworkPayload.getHash());
            });

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

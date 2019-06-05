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

import bisq.core.offer.OfferPayload;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.network.NetworkEnvelope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Demo Stats metric derived from the OfferPayload messages we get from the seed nodes
 * 
 * @author Florian Reimair
 */
@Slf4j
public class P2PMarketStats extends P2PSeedNodeSnapshotBase {
    private final Set<byte[]> hashes = new TreeSet<>(Arrays::compare);

    /**
     * Efficient way to count occurrences.
     */
    private class Counter {
        private long value = 0;

        synchronized long value() {
            return value;
        }

        synchronized void increment() {
            value++;
        }
    }

    private class MyStatistics implements Statistics<Counter> {
        private final Map<String, Counter> buckets = new HashMap<>();

        @Override
        public Statistics create() {
            return new MyStatistics();
        }

        @Override
        public synchronized void log(Object message) {

            if(message instanceof OfferPayload) {
                OfferPayload currentMessage = (OfferPayload) message;
                // For logging different data types
                String market = currentMessage.getDirection() + "." + currentMessage.getBaseCurrencyCode() + "_" + currentMessage.getCounterCurrencyCode();

                buckets.putIfAbsent(market, new Counter());
                buckets.get(market).increment();
            }
        }

        @Override
        public Map<String, Counter> values() {
            return buckets;
        }

        @Override
        public void reset() {
            buckets.clear();
        }
    }

    public P2PMarketStats(Reporter graphiteReporter) {
        super(graphiteReporter);

        statistics = new MyStatistics();
    }

    @Override
    protected List<NetworkEnvelope> getRequests() {
        List<NetworkEnvelope> result = new ArrayList<>();

        Random random = new Random();
        result.add(new PreliminaryGetDataRequest(random.nextInt(), hashes));

        return result;
    }

    @Override
    protected void report() {
        Map<String, String> report = new HashMap<>();
        bucketsPerHost.forEach((host, statistics) -> statistics.values().forEach((market, numberOfOffers) -> report.put(OnionParser.prettyPrint(host) + "." + market.toString(), String.valueOf(((Counter) numberOfOffers).value()))));

        reporter.report(report, getName());
    }

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
        }
        return false;
    }
}

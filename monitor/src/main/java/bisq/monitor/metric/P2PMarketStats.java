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

import bisq.core.account.witness.AccountAgeWitnessStore;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.offer.OfferPayload;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.trade.statistics.TradeStatistics2Store;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.storage.Storage;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Demo Stats metric derived from the OfferPayload messages we get from the seed nodes
 *
 * @author Florian Reimair
 */
@Slf4j
public class P2PMarketStats extends P2PSeedNodeSnapshotBase {
    private static final String DATABASE_DIR = "run.dbDir";

    private final Set<byte[]> hashes = new TreeSet<>(Arrays::compare);

    final Map<NodeAddress, Statistics<Counter>> versionBucketsPerHost = new ConcurrentHashMap<>();

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

    private class VersionsStatistics implements Statistics<Counter> {
        private final Map<String, Counter> buckets = new HashMap<>();

        @Override
        public Statistics create() {
            return new VersionsStatistics();
        }

        @Override
        public void log(Object message) {

            if (message instanceof OfferPayload) {
                OfferPayload currentMessage = (OfferPayload) message;

                String version = "v" + currentMessage.getId().substring(currentMessage.getId().lastIndexOf("-") + 1);

                buckets.putIfAbsent(version, new Counter());
                buckets.get(version).increment();
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
    public void configure(Properties properties) {
        super.configure(properties);

        if (hashes.isEmpty() && configuration.getProperty(DATABASE_DIR) != null) {
            File dir = new File(configuration.getProperty(DATABASE_DIR));
            String networkPostfix = "_" + BaseCurrencyNetwork.values()[Version.getBaseCurrencyNetwork()].toString();
            try {
                Storage<PersistableEnvelope> storage = new Storage<>(dir, new CorePersistenceProtoResolver(null, null, null, null), null);
                TradeStatistics2Store tradeStatistics2Store = (TradeStatistics2Store) storage.initAndGetPersistedWithFileName(TradeStatistics2Store.class.getSimpleName() + networkPostfix, 0);
                hashes.addAll(tradeStatistics2Store.getMap().keySet().stream().map(byteArray -> byteArray.bytes).collect(Collectors.toList()));

                AccountAgeWitnessStore accountAgeWitnessStore = (AccountAgeWitnessStore) storage.initAndGetPersistedWithFileName(AccountAgeWitnessStore.class.getSimpleName() + networkPostfix, 0);
                hashes.addAll(accountAgeWitnessStore.getMap().keySet().stream().map(byteArray -> byteArray.bytes).collect(Collectors.toList()));
            } catch (NullPointerException e) {
                // in case there is no store file
                log.error("There is no storage file where there should be one: {}", dir.getAbsolutePath());
            }
        }
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
        bucketsPerHost.forEach((host, statistics) -> statistics.values().forEach((market, numberOfOffers) -> report.put(OnionParser.prettyPrint(host) + "." + market, String.valueOf(((Counter) numberOfOffers).value()))));

        reporter.report(report, getName());

        // do version statistics
        report.clear();
        versionBucketsPerHost.values().stream().findAny().get().values().forEach((version, numberOfOccurrences) -> report.put(version, String.valueOf(numberOfOccurrences.value())));
        reporter.report(report, "versions");
    }

    protected boolean treatMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        checkNotNull(connection.getPeersNodeAddressProperty(),
                "although the property is nullable, we need it to not be null");

        if (networkEnvelope instanceof GetDataResponse) {

            Statistics result = this.statistics.create();
            VersionsStatistics versions = new VersionsStatistics();

            GetDataResponse dataResponse = (GetDataResponse) networkEnvelope;
            final Set<ProtectedStorageEntry> dataSet = dataResponse.getDataSet();
            dataSet.forEach(e -> {
                final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                if (protectedStoragePayload == null) {
                    log.warn("StoragePayload was null: {}", networkEnvelope.toString());
                    return;
                }

                result.log(protectedStoragePayload);
                versions.log(protectedStoragePayload);
            });

            dataResponse.getPersistableNetworkPayloadSet().forEach(persistableNetworkPayload -> {
                // memorize message hashes
                //Byte[] bytes = new Byte[persistableNetworkPayload.getHash().length];
                //Arrays.setAll(bytes, n -> persistableNetworkPayload.getHash()[n]);

                //hashes.add(bytes);

                hashes.add(persistableNetworkPayload.getHash());
            });

            bucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), result);
            versionBucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), versions);
            return true;
        }
        return false;
    }
}

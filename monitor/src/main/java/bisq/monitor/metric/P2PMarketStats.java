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

import bisq.monitor.Reporter;

import bisq.core.offer.OfferUtil;
import bisq.core.offer.bisq_v1.OfferPayload;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.network.NetworkEnvelope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
    final Map<NodeAddress, Statistics<Aggregator>> versionBucketsPerHost = new ConcurrentHashMap<>();
    final Map<NodeAddress, Statistics<Aggregator>> offerVolumeBucketsPerHost = new ConcurrentHashMap<>();
    final Map<NodeAddress, Statistics<List<Long>>> offerVolumeDistributionBucketsPerHost = new ConcurrentHashMap<>();
    final Map<NodeAddress, Statistics<Map<NodeAddress, Aggregator>>> offersPerTraderBucketsPerHost = new ConcurrentHashMap<>();
    final Map<NodeAddress, Statistics<Map<NodeAddress, Aggregator>>> volumePerTraderBucketsPerHost = new ConcurrentHashMap<>();

    /**
     * Efficient way to aggregate numbers.
     */
    private static class Aggregator {
        private long value = 0;

        synchronized long value() {
            return value;
        }

        synchronized void increment() {
            value++;
        }

        synchronized void add(long amount) {
            value += amount;
        }
    }

    private abstract static class OfferStatistics<T> extends Statistics<T> {
        @Override
        public synchronized void log(Object message) {
            if (message instanceof OfferPayload) {
                OfferPayload currentMessage = (OfferPayload) message;
                // For logging different data types
                String market = currentMessage.getDirection() + "." + currentMessage.getBaseCurrencyCode() + "_" + currentMessage.getCounterCurrencyCode();

                process(market, currentMessage);
            }
        }

        abstract void process(String market, OfferPayload currentMessage);
    }

    private class OfferCountStatistics extends OfferStatistics<Aggregator> {

        @Override
        void process(String market, OfferPayload currentMessage) {
            buckets.putIfAbsent(market, new Aggregator());
            buckets.get(market).increment();
        }
    }

    private class OfferVolumeStatistics extends OfferStatistics<Aggregator> {

        @Override
        void process(String market, OfferPayload currentMessage) {
            buckets.putIfAbsent(market, new Aggregator());
            buckets.get(market).add(currentMessage.getAmount());
        }
    }

    private class OfferVolumeDistributionStatistics extends OfferStatistics<List<Long>> {

        @Override
        void process(String market, OfferPayload currentMessage) {
            buckets.putIfAbsent(market, new ArrayList<>());
            buckets.get(market).add(currentMessage.getAmount());
        }
    }

    private class OffersPerTraderStatistics extends OfferStatistics<Map<NodeAddress, Aggregator>> {

        @Override
        void process(String market, OfferPayload currentMessage) {
            buckets.putIfAbsent(market, new HashMap<>());
            buckets.get(market).putIfAbsent(currentMessage.getOwnerNodeAddress(), new Aggregator());
            buckets.get(market).get(currentMessage.getOwnerNodeAddress()).increment();
        }
    }

    private class VolumePerTraderStatistics extends OfferStatistics<Map<NodeAddress, Aggregator>> {

        @Override
        void process(String market, OfferPayload currentMessage) {
            buckets.putIfAbsent(market, new HashMap<>());
            buckets.get(market).putIfAbsent(currentMessage.getOwnerNodeAddress(), new Aggregator());
            buckets.get(market).get(currentMessage.getOwnerNodeAddress()).add(currentMessage.getAmount());
        }
    }

    private class VersionsStatistics extends Statistics<Aggregator> {

        @Override
        public void log(Object message) {

            if (message instanceof OfferPayload) {
                OfferPayload offerPayload = (OfferPayload) message;
                String version = "v" + OfferUtil.getVersionFromId(offerPayload.getId());
                buckets.putIfAbsent(version, new Aggregator());
                buckets.get(version).increment();
            }
        }
    }

    public P2PMarketStats(Reporter graphiteReporter) {
        super(graphiteReporter);
    }

    @Override
    protected List<NetworkEnvelope> getRequests() {
        List<NetworkEnvelope> result = new ArrayList<>();

        Random random = new Random();
        result.add(new PreliminaryGetDataRequest(random.nextInt(), hashes));

        return result;
    }

    protected void createHistogram(List<Long> input, String market, Map<String, String> report) {
        int numberOfBins = 5;

        // - get biggest offer
        double max = input.stream().max(Long::compareTo).map(value -> value * 1.01).orElse(0.0);

        // - create histogram
        input.stream().collect(
                Collectors.groupingBy(aLong -> aLong == max ? numberOfBins - 1 : (int) Math.floor(aLong / (max / numberOfBins)), Collectors.counting())).
                forEach((integer, integer2) -> report.put(market + ".bin_" + integer, String.valueOf(integer2)));

        report.put(market + ".number_of_bins", String.valueOf(numberOfBins));
        report.put(market + ".max", String.valueOf((int) max));
    }

    @Override
    protected void report() {
        Map<String, String> report = new HashMap<>();
        bucketsPerHost.values().stream().findFirst().ifPresent(nodeAddressStatisticsEntry -> nodeAddressStatisticsEntry.values().forEach((market, numberOfOffers) -> report.put(market, String.valueOf(((Aggregator) numberOfOffers).value()))));
        reporter.report(report, getName() + ".offerCount");

        // do offerbook volume statistics
        report.clear();
        offerVolumeBucketsPerHost.values().stream().findFirst().ifPresent(aggregatorStatistics -> aggregatorStatistics.values().forEach((market, numberOfOffers) -> report.put(market, String.valueOf(numberOfOffers.value()))));
        reporter.report(report, getName() + ".volume");

        // do the offer vs volume histogram
        report.clear();
        // - get a data set
        offerVolumeDistributionBucketsPerHost.values().stream().findFirst().ifPresent(listStatistics -> listStatistics.values().forEach((market, offers) -> {
            createHistogram(offers, market, report);
        }));
        reporter.report(report, getName() + ".volume-per-offer-distribution");

        // do offers per trader
        report.clear();
        // - get a data set
        offersPerTraderBucketsPerHost.values().stream().findFirst().ifPresent(mapStatistics -> mapStatistics.values().forEach((market, stuff) -> {
            List<Long> offerPerTrader = stuff.values().stream().map(Aggregator::value).collect(Collectors.toList());

            createHistogram(offerPerTrader, market, report);
        }));
        reporter.report(report, getName() + ".traders_by_number_of_offers");

        // do volume per trader
        report.clear();
        // - get a data set
        volumePerTraderBucketsPerHost.values().stream().findFirst().ifPresent(mapStatistics -> mapStatistics.values().forEach((market, stuff) -> {
            List<Long> volumePerTrader = stuff.values().stream().map(Aggregator::value).collect(Collectors.toList());

            createHistogram(volumePerTrader, market, report);
        }));
        reporter.report(report, getName() + ".traders_by_volume");

        // do version statistics
        report.clear();
        Optional<Statistics<Aggregator>> optionalStatistics = versionBucketsPerHost.values().stream().findAny();
        optionalStatistics.ifPresent(aggregatorStatistics -> aggregatorStatistics.values()
                .forEach((version, numberOfOccurrences) -> report.put(version, String.valueOf(numberOfOccurrences.value()))));
        reporter.report(report, "versions");
    }

    protected boolean treatMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        checkNotNull(connection.getPeersNodeAddressProperty(),
                "although the property is nullable, we need it to not be null");

        if (networkEnvelope instanceof GetDataResponse) {

            Statistics offerCount = new OfferCountStatistics();
            Statistics offerVolume = new OfferVolumeStatistics();
            Statistics offerVolumeDistribution = new OfferVolumeDistributionStatistics();
            Statistics offersPerTrader = new OffersPerTraderStatistics();
            Statistics volumePerTrader = new VolumePerTraderStatistics();
            Statistics versions = new VersionsStatistics();

            GetDataResponse dataResponse = (GetDataResponse) networkEnvelope;
            final Set<ProtectedStorageEntry> dataSet = dataResponse.getDataSet();
            dataSet.forEach(e -> {
                final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                if (protectedStoragePayload == null) {
                    log.warn("StoragePayload was null: {}", networkEnvelope.toString());
                    return;
                }

                offerCount.log(protectedStoragePayload);
                offerVolume.log(protectedStoragePayload);
                offerVolumeDistribution.log(protectedStoragePayload);
                offersPerTrader.log(protectedStoragePayload);
                volumePerTrader.log(protectedStoragePayload);
                versions.log(protectedStoragePayload);
            });

            dataResponse.getPersistableNetworkPayloadSet().forEach(persistableNetworkPayload -> {
                // memorize message hashes
                //Byte[] bytes = new Byte[persistableNetworkPayload.getHash().length];
                //Arrays.setAll(bytes, n -> persistableNetworkPayload.getHash()[n]);

                //hashes.add(bytes);

                hashes.add(persistableNetworkPayload.getHash());
            });

            bucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), offerCount);
            offerVolumeBucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), offerVolume);
            offerVolumeDistributionBucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), offerVolumeDistribution);
            offersPerTraderBucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), offersPerTrader);
            volumePerTraderBucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), volumePerTrader);
            versionBucketsPerHost.put(connection.getPeersNodeAddressProperty().getValue(), versions);
            return true;
        }
        return false;
    }
}

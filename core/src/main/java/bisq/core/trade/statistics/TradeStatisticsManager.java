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

package bisq.core.trade.statistics;

import bisq.core.locale.CurrencyTuple;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.BuyerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;

import com.google.inject.Inject;

import javax.inject.Named;
import javax.inject.Singleton;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.time.Instant;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Singleton
@Slf4j
public class TradeStatisticsManager {
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final TradeStatisticsConverter tradeStatisticsConverter;
    private final File storageDir;
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics3> observableTradeStatisticsSet = FXCollections.observableSet();
    private JsonFileManager jsonFileManager;

    @Inject
    public TradeStatisticsManager(P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  TradeStatistics3StorageService tradeStatistics3StorageService,
                                  AppendOnlyDataStoreService appendOnlyDataStoreService,
                                  TradeStatisticsConverter tradeStatisticsConverter,
                                  @Named(Config.STORAGE_DIR) File storageDir,
                                  @Named(Config.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.tradeStatistics3StorageService = tradeStatistics3StorageService;
        this.tradeStatisticsConverter = tradeStatisticsConverter;
        this.storageDir = storageDir;
        this.dumpStatistics = dumpStatistics;

        appendOnlyDataStoreService.addService(tradeStatistics3StorageService);
    }

    public void shutDown() {
        tradeStatisticsConverter.shutDown();
        if (jsonFileManager != null) {
            jsonFileManager.shutDown();
        }
    }

    public void onAllServicesInitialized() {
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof TradeStatistics3) {
                TradeStatistics3 tradeStatistics = (TradeStatistics3) payload;
                if (!tradeStatistics.isValid()) {
                    return;
                }
                observableTradeStatisticsSet.add(tradeStatistics);
                priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
                maybeDumpStatistics();
            }
        });

        Set<TradeStatistics3> set = tradeStatistics3StorageService.getMapOfAllData().values().stream()
                .filter(e -> e instanceof TradeStatistics3)
                .map(e -> (TradeStatistics3) e)
                .filter(TradeStatistics3::isValid)
                .collect(Collectors.toSet());
        observableTradeStatisticsSet.addAll(set);
        priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
        maybeDumpStatistics();
    }

    public ObservableSet<TradeStatistics3> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }

    private void maybeDumpStatistics() {
        if (!dumpStatistics) {
            return;
        }

        if (jsonFileManager == null) {
            jsonFileManager = new JsonFileManager(storageDir);

            // We only dump once the currencies as they do not change during runtime
            ArrayList<CurrencyTuple> fiatCurrencyList = CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toCollection(ArrayList::new));
            jsonFileManager.writeToDiscThreaded(JsonUtil.objectToJson(fiatCurrencyList), "fiat_currency_list");

            ArrayList<CurrencyTuple> cryptoCurrencyList = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toCollection(ArrayList::new));
            cryptoCurrencyList.add(0, new CurrencyTuple(Res.getBaseCurrencyCode(), Res.getBaseCurrencyName(), 8));
            jsonFileManager.writeToDiscThreaded(JsonUtil.objectToJson(cryptoCurrencyList), "crypto_currency_list");

            Instant yearAgo = Instant.ofEpochSecond(Instant.now().getEpochSecond() - TimeUnit.DAYS.toSeconds(365));
            Set<String> activeCurrencies = observableTradeStatisticsSet.stream()
                    .filter(e -> e.getDate().toInstant().isAfter(yearAgo))
                    .map(p -> p.getCurrency())
                    .collect(Collectors.toSet());

            ArrayList<CurrencyTuple> activeFiatCurrencyList = fiatCurrencyList.stream()
                    .filter(e -> activeCurrencies.contains(e.code))
                    .map(e -> new CurrencyTuple(e.code, e.name, 8))
                    .collect(Collectors.toCollection(ArrayList::new));
            jsonFileManager.writeToDiscThreaded(JsonUtil.objectToJson(activeFiatCurrencyList), "active_fiat_currency_list");

            ArrayList<CurrencyTuple> activeCryptoCurrencyList = cryptoCurrencyList.stream()
                    .filter(e -> activeCurrencies.contains(e.code))
                    .map(e -> new CurrencyTuple(e.code, e.name, 8))
                    .collect(Collectors.toCollection(ArrayList::new));
            jsonFileManager.writeToDiscThreaded(JsonUtil.objectToJson(activeCryptoCurrencyList), "active_crypto_currency_list");
        }

        List<TradeStatisticsForJson> list = observableTradeStatisticsSet.stream()
                .map(TradeStatisticsForJson::new)
                .sorted((o1, o2) -> (Long.compare(o2.tradeDate, o1.tradeDate)))
                .collect(Collectors.toList());
        TradeStatisticsForJson[] array = new TradeStatisticsForJson[list.size()];
        list.toArray(array);
        jsonFileManager.writeToDiscThreaded(JsonUtil.objectToJson(array), "trade_statistics");
    }

    public void maybeRepublishTradeStatistics(Set<TradeModel> trades,
                                              @Nullable String referralId,
                                              boolean isTorNetworkNode) {
        long ts = System.currentTimeMillis();
        Set<P2PDataStorage.ByteArray> hashes = tradeStatistics3StorageService.getMapOfAllData().keySet();
        trades.stream()
                .filter(tradable -> tradable instanceof Trade)
                .forEach(tradable -> {
                    Trade trade = (Trade) tradable;
                    if (trade instanceof BuyerTrade) {
                        log.debug("Trade: {} is a buyer trade, we only republish we have been seller.",
                                trade.getShortId());
                        return;
                    }

                    TradeStatistics3 tradeStatistics3 = TradeStatistics3.from(trade, referralId, isTorNetworkNode);
                    boolean hasTradeStatistics3 = hashes.contains(new P2PDataStorage.ByteArray(tradeStatistics3.getHash()));
                    if (hasTradeStatistics3) {
                        log.debug("Trade: {}. We have already a tradeStatistics matching the hash of tradeStatistics3.",
                                trade.getShortId());
                        return;
                    }

                    // If we did not find a TradeStatistics3 we look up if we find a TradeStatistics3 converted from
                    // TradeStatistics2 where we used the original hash, which is not the native hash of the
                    // TradeStatistics3 but of TradeStatistics2.
                    if (!trade.isBsqSwap()) {
                        TradeStatistics2 tradeStatistics2 = TradeStatistics2.from(trade, referralId, isTorNetworkNode);
                        boolean hasTradeStatistics2 = hashes.contains(new P2PDataStorage.ByteArray(tradeStatistics2.getHash()));
                        if (hasTradeStatistics2) {
                            log.debug("Trade: {}. We have already a tradeStatistics matching the hash of tradeStatistics2. ",
                                    trade.getShortId());
                            return;
                        }
                    }

                    if (!tradeStatistics3.isValid()) {
                        log.warn("Trade: {}. Trade statistics is invalid. We do not publish it.", tradeStatistics3);
                        return;
                    }

                    log.info("Trade: {}. We republish tradeStatistics3 as we did not find it in the existing trade statistics. ",
                            trade.getShortId());
                    p2PService.addPersistableNetworkPayload(tradeStatistics3, true);
                });
        log.info("maybeRepublishTradeStatistics took {} ms. Number of tradeStatistics: {}. Number of own trades: {}",
                System.currentTimeMillis() - ts, hashes.size(), trades.size());
    }
}

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
import bisq.core.monetary.Price;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.BuyerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.AveragePriceUtil;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.util.RangeUtils;

import com.google.inject.Inject;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Range;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.time.Instant;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Singleton
@Slf4j
public class TradeStatisticsManager {
    // No price feed provider delivers BSQ, so its market price comes from the BSQ trades. Their prices differ widely
    // from trade to trade, so it is the volume weighted average price of the BSQ trades in this period up to the
    // newest one. With about 10 BSQ trades per day it covers several trades and follows the market within a day.
    public static final long BSQ_MARKET_PRICE_PERIOD = TimeUnit.DAYS.toMillis(1);
    // Any peer can publish statistics without limit, so the average reads only the newest BSQ statistics of the period.
    // No day of the history up to 2026 had more than 49 BSQ trades.
    @VisibleForTesting
    static final int MAX_BSQ_TRADES_OF_MARKET_PRICE = 200;

    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final File storageDir;
    private final boolean dumpStatistics;
    private final NavigableSet<TradeStatistics3> navigableTradeStatisticsSet = new TreeSet<>();
    private final ObservableSet<TradeStatistics3> observableTradeStatisticsSet = FXCollections.observableSet(navigableTradeStatisticsSet);
    // Kept up to date as statistics arrive, so that a received statistic costs no search through all statistics
    private final Map<String, TradeStatistics3> newestTradeStatisticsByCurrency = new HashMap<>();
    private final NavigableSet<TradeStatistics3> bsqTradeStatisticsSet = new TreeSet<>();
    private final Set<String> currenciesOfReceivedStatistics = new HashSet<>();
    private boolean marketPriceUpdatePending;
    private JsonFileManager jsonFileManager;

    @Inject
    public TradeStatisticsManager(P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  TradeStatistics3StorageService tradeStatistics3StorageService,
                                  AppendOnlyDataStoreService appendOnlyDataStoreService,
                                  @Named(Config.STORAGE_DIR) File storageDir,
                                  @Named(Config.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.tradeStatistics3StorageService = tradeStatistics3StorageService;
        this.storageDir = storageDir;
        this.dumpStatistics = dumpStatistics;

        appendOnlyDataStoreService.addService(tradeStatistics3StorageService);
    }

    public void shutDown() {
        if (jsonFileManager != null) {
            jsonFileManager.shutDown();
        }
    }

    public void onAllServicesInitialized() {
        // tradestatistics listener for updating last traded price
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof TradeStatistics3) {
                TradeStatistics3 tradeStatistics = (TradeStatistics3) payload;
                if (!tradeStatistics.isValid()) {
                    return;
                }
                add(tradeStatistics);
                currenciesOfReceivedStatistics.add(tradeStatistics.getCurrency());
                requestMarketPriceUpdate();
            }
        });

        tradeStatistics3StorageService.getMapOfAllData().values().stream()
                .filter(e -> e instanceof TradeStatistics3)
                .map(e -> (TradeStatistics3) e)
                .filter(TradeStatistics3::isValid)
                .forEach(this::add);

        Map<String, Price> marketPriceByCurrencyCode = new HashMap<>();
        newestTradeStatisticsByCurrency.forEach((currencyCode, newest) ->
                marketPriceByCurrencyCode.put(currencyCode, getBisqMarketPrice(newest)));
        priceFeedService.applyInitialBisqMarketPrice(marketPriceByCurrencyCode);
        maybeDumpStatistics();
    }

    // A statistic can arrive after newer ones of its currency, for example when a trader republishes it
    private void add(TradeStatistics3 tradeStatistics) {
        observableTradeStatisticsSet.add(tradeStatistics);
        String currencyCode = tradeStatistics.getCurrency();
        if (currencyCode.equals("BSQ")) {
            bsqTradeStatisticsSet.add(tradeStatistics);
        }
        newestTradeStatisticsByCurrency.merge(currencyCode, tradeStatistics,
                (newest, added) -> added.compareTo(newest) > 0 ? added : newest);
    }

    // The statistics of a bundle arrive as one task each on the user thread. The update runs after the tasks which wait
    // at that time, so that it derives the prices, notifies the listeners of the price feed and writes the dump once for
    // them, not once per statistic.
    private void requestMarketPriceUpdate() {
        if (marketPriceUpdatePending) {
            return;
        }
        marketPriceUpdatePending = true;
        UserThread.execute(() -> {
            marketPriceUpdatePending = false;
            Map<String, Price> marketPriceByCurrencyCode = new HashMap<>();
            currenciesOfReceivedStatistics.forEach(currencyCode -> marketPriceByCurrencyCode.put(currencyCode,
                    getBisqMarketPrice(newestTradeStatisticsByCurrency.get(currencyCode))));
            currenciesOfReceivedStatistics.clear();
            priceFeedService.setBisqMarketPrices(marketPriceByCurrencyCode);
            maybeDumpStatistics();
        });
    }

    // The market price of a currency for which the price feed has no price, from the newest statistic of the currency
    private Price getBisqMarketPrice(TradeStatistics3 newest) {
        if (!newest.getCurrency().equals("BSQ")) {
            return newest.getTradePrice();
        }

        long newestDate = newest.getDateAsLong();
        List<TradeStatistics3> bsqTradeStatistics = RangeUtils.subSet(bsqTradeStatisticsSet)
                .withKey(TradeStatistics3::getDateAsLong)
                .overRange(Range.openClosed(newestDate - BSQ_MARKET_PRICE_PERIOD, newestDate))
                .descendingSet().stream()
                .limit(MAX_BSQ_TRADES_OF_MARKET_PRICE)
                .collect(Collectors.toList());
        long averagePrice = AveragePriceUtil.getBTCAverage(bsqTradeStatistics);
        // 0 stands for no average. Valid statistics do not give it, and the market price must not be 0.
        return averagePrice > 0 ? Price.valueOf("BSQ", averagePrice) : newest.getTradePrice();
    }

    public NavigableSet<TradeStatistics3> getNavigableTradeStatisticsSet() {
        return Collections.unmodifiableNavigableSet(navigableTradeStatisticsSet);
    }

    public ObservableSet<TradeStatistics3> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }

    public List<TradeStatistics3> getTradeStatisticsList(long dateStart, long dateEnd) {
        return new ArrayList<>(RangeUtils.subSet(navigableTradeStatisticsSet)
                .withKey(TradeStatistics3::getDateAsLong)
                .overRange(Range.openClosed(Math.min(dateStart, dateEnd), dateEnd))
                .descendingSet());
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
                    .map(TradeStatistics3::getCurrency)
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

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

import bisq.core.app.AppOptionKeys;
import bisq.core.locale.CurrencyTuple;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.UserThread;
import bisq.common.storage.JsonFileManager;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.io.File;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TradeStatisticsManager {

    static TradeStatistics2 ConvertToTradeStatistics2(TradeStatistics tradeStatistics) {
        return new TradeStatistics2(tradeStatistics.getDirection(),
                tradeStatistics.getBaseCurrency(),
                tradeStatistics.getCounterCurrency(),
                tradeStatistics.getOfferPaymentMethod(),
                tradeStatistics.getOfferDate(),
                tradeStatistics.isOfferUseMarketBasedPrice(),
                tradeStatistics.getOfferMarketPriceMargin(),
                tradeStatistics.getOfferAmount(),
                tradeStatistics.getOfferMinAmount(),
                tradeStatistics.getOfferId(),
                tradeStatistics.getTradePrice().getValue(),
                tradeStatistics.getTradeAmount().getValue(),
                tradeStatistics.getTradeDate().getTime(),
                tradeStatistics.getDepositTxId(),
                null,
                tradeStatistics.getExtraDataMap());
    }

    private final JsonFileManager jsonFileManager;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final ReferralIdService referralIdService;
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics2> observableTradeStatisticsSet = FXCollections.observableSet();

    @Inject
    public TradeStatisticsManager(P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  TradeStatistics2StorageService tradeStatistics2StorageService,
                                  AppendOnlyDataStoreService appendOnlyDataStoreService,
                                  ReferralIdService referralIdService,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(AppOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.referralIdService = referralIdService;
        this.dumpStatistics = dumpStatistics;
        jsonFileManager = new JsonFileManager(storageDir);

        appendOnlyDataStoreService.addService(tradeStatistics2StorageService);
    }

    public void onAllServicesInitialized() {
        if (dumpStatistics) {
            ArrayList<CurrencyTuple> fiatCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            jsonFileManager.writeToDisc(Utilities.objectToJson(fiatCurrencyList), "fiat_currency_list");

            ArrayList<CurrencyTuple> cryptoCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            cryptoCurrencyList.add(0, new CurrencyTuple(Res.getBaseCurrencyCode(), Res.getBaseCurrencyName(), 8));
            jsonFileManager.writeToDisc(Utilities.objectToJson(cryptoCurrencyList), "crypto_currency_list");
        }

        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof TradeStatistics2)
                addToMap((TradeStatistics2) payload, true);
        });

        Map<String, TradeStatistics2> map = new HashMap<>();
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().stream()
                .filter(e -> e instanceof TradeStatistics2)
                .forEach(e -> addToMap((TradeStatistics2) e, map));
        observableTradeStatisticsSet.addAll(map.values());

        priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
        dump();

        // print all currencies sorted by nr. of trades
        // printAllCurrencyStats();
    }

    public void publishTradeStatistics(List<Trade> trades) {
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);

            Map<String, String> extraDataMap = null;
            if (referralIdService.getOptionalReferralId().isPresent()) {
                extraDataMap = new HashMap<>();
                extraDataMap.put(OfferPayload.REFERRAL_ID, referralIdService.getOptionalReferralId().get());
            }
            Offer offer = trade.getOffer();
            checkNotNull(offer, "offer must not ne null");
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not ne null");
            TradeStatistics2 tradeStatistics = new TradeStatistics2(offer.getOfferPayload(),
                    trade.getTradePrice(),
                    trade.getTradeAmount(),
                    trade.getDate(),
                    (trade.getDepositTx() != null ? trade.getDepositTx().getHashAsString() : ""),
                    extraDataMap);
            addToMap(tradeStatistics, true);

            // We only republish trades from last 10 days
            if ((new Date().getTime() - trade.getDate().getTime()) < TimeUnit.DAYS.toMillis(10)) {
                long delay = 5000;
                long minDelay = (i + 1) * delay;
                long maxDelay = (i + 2) * delay;
                UserThread.runAfterRandomDelay(() -> {
                    p2PService.addPersistableNetworkPayload(tradeStatistics, true);
                }, minDelay, maxDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void addToMap(TradeStatistics2 tradeStatistics, boolean storeLocally) {
        if (!observableTradeStatisticsSet.contains(tradeStatistics)) {
            boolean itemAlreadyAdded = observableTradeStatisticsSet.stream()
                    .anyMatch(e -> (e.getOfferId().equals(tradeStatistics.getOfferId())));
            if (!itemAlreadyAdded) {
                observableTradeStatisticsSet.add(tradeStatistics);
                if (storeLocally) {
                    priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
                    dump();
                }
            } else {
                log.debug("We have already an item with the same offer ID. That might happen if both the maker and the taker published the tradeStatistics");
            }
        }
    }

    public void addToMap(TradeStatistics2 tradeStatistics, Map<String, TradeStatistics2> map) {
        TradeStatistics2 prevValue = map.putIfAbsent(tradeStatistics.getOfferId(), tradeStatistics);
        if (prevValue != null)
            log.debug("We have already an item with the same offer ID. That might happen if both the maker and the taker published the tradeStatistics");
    }

    public ObservableSet<TradeStatistics2> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }

    private void dump() {
        if (dumpStatistics) {
            // We store the statistics as json so it is easy for further processing (e.g. for web based services)
            // TODO This is just a quick solution for storing to one file.
            // 1 statistic entry has 500 bytes as json.
            // Need a more scalable solution later when we get more volume.
            // The flag will only be activated by dedicated nodes, so it should not be too critical for the moment, but needs to
            // get improved. Maybe a LevelDB like DB...? Could be impl. in a headless version only.
            List<TradeStatisticsForJson> list = observableTradeStatisticsSet.stream().map(TradeStatisticsForJson::new).collect(Collectors.toList());
            list.sort((o1, o2) -> (o1.tradeDate < o2.tradeDate ? 1 : (o1.tradeDate == o2.tradeDate ? 0 : -1)));
            TradeStatisticsForJson[] array = new TradeStatisticsForJson[list.size()];
            list.toArray(array);
            jsonFileManager.writeToDisc(Utilities.objectToJson(array), "trade_statistics");
        }
    }

    // To have automatic check and removal we would need new fields in the asset class for the date when it was
    // added/released and a property if it was removed due either getting blocked by the DAO stakehodlers in voting or
    // removed due lack of activity.
    // For now we use the old script below to print the usage of the coins.
    private void printAllCurrencyStats() {
        Map<String, Set<TradeStatistics2>> map1 = new HashMap<>();
        for (TradeStatistics2 tradeStatistics : observableTradeStatisticsSet) {
            if (CurrencyUtil.isFiatCurrency(tradeStatistics.getCounterCurrency())) {
                String counterCurrency = CurrencyUtil.getNameAndCode(tradeStatistics.getCounterCurrency());
                if (!map1.containsKey(counterCurrency))
                    map1.put(counterCurrency, new HashSet<>());

                map1.get(counterCurrency).add(tradeStatistics);
            }
        }

        StringBuilder sb1 = new StringBuilder("\nAll traded Fiat currencies:\n");
        map1.entrySet().stream()
                .sorted((o1, o2) -> Integer.compare(o2.getValue().size(), o1.getValue().size()))
                .forEach(e -> sb1.append(e.getKey()).append(": ").append(e.getValue().size()).append("\n"));
        log.error(sb1.toString());

        Map<String, Set<TradeStatistics2>> map2 = new HashMap<>();
        for (TradeStatistics2 tradeStatistics : observableTradeStatisticsSet) {
            if (CurrencyUtil.isCryptoCurrency(tradeStatistics.getBaseCurrency())) {
                final String code = CurrencyUtil.getNameAndCode(tradeStatistics.getBaseCurrency());
                if (!map2.containsKey(code))
                    map2.put(code, new HashSet<>());

                map2.get(code).add(tradeStatistics);
            }
        }

        List<String> allCryptoCurrencies = new ArrayList<>();
        Set<String> coinsWithValidator = new HashSet<>();

        // List of coins with validator before 0.6.0 hard requirements for address validator
        coinsWithValidator.add("BTC");
        coinsWithValidator.add("BSQ");
        coinsWithValidator.add("LTC");
        coinsWithValidator.add("DOGE");
        coinsWithValidator.add("DASH");
        coinsWithValidator.add("ETH");
        coinsWithValidator.add("PIVX");
        coinsWithValidator.add("IOP");
        coinsWithValidator.add("888");
        coinsWithValidator.add("ZEC");
        coinsWithValidator.add("GBYTE");
        coinsWithValidator.add("NXT");

        // All those need to have a address validator
        Set<String> newlyAdded = new HashSet<>();
        // v0.6.0
        newlyAdded.add("DCT");
        newlyAdded.add("PNC");
        newlyAdded.add("WAC");
        newlyAdded.add("ZEN");
        newlyAdded.add("ELLA");
        newlyAdded.add("XCN");
        newlyAdded.add("TRC");
        newlyAdded.add("INXT");
        newlyAdded.add("PART");
        // v0.6.1
        newlyAdded.add("MAD");
        newlyAdded.add("BCH");
        newlyAdded.add("BCHC");
        newlyAdded.add("BTG");
        // v0.6.2
        newlyAdded.add("CAGE");
        newlyAdded.add("CRED");
        newlyAdded.add("XSPEC");
        // v0.6.3
        newlyAdded.add("WILD");
        newlyAdded.add("ONION");
        // v0.6.4
        newlyAdded.add("CREA");
        newlyAdded.add("XIN");
        // v0.6.5
        newlyAdded.add("BETR");
        newlyAdded.add("MVT");
        newlyAdded.add("REF");
        // v0.6.6
        newlyAdded.add("STL");
        newlyAdded.add("DAI");
        newlyAdded.add("YTN");
        newlyAdded.add("DARX");
        newlyAdded.add("ODN");
        newlyAdded.add("CDT");
        newlyAdded.add("DGM");
        newlyAdded.add("SCS");
        newlyAdded.add("SOS");
        newlyAdded.add("ACH");
        newlyAdded.add("VDN");
        // v0.7.0
        newlyAdded.add("ALC");
        newlyAdded.add("DIN");
        newlyAdded.add("NAH");
        newlyAdded.add("ROI");
        newlyAdded.add("WMCC");
        newlyAdded.add("RTO");
        newlyAdded.add("KOTO");
        newlyAdded.add("PHR");
        newlyAdded.add("UBQ");
        newlyAdded.add("QWARK");
        newlyAdded.add("GEO");
        newlyAdded.add("GRANS");
        newlyAdded.add("ICH");

        // TODO add remaining coins since 0.7.0
        //newlyAdded.clear();
       /* new AssetRegistry().stream()
                .sorted(Comparator.comparing(o -> o.getName().toLowerCase()))
                .filter(e -> !e.getTickerSymbol().equals("BSQ")) // BSQ is not out yet...
                .filter(e -> !e.getTickerSymbol().equals("BTC"))
                .map(e -> e.getTickerSymbol()) // We want to get rid of duplicated entries for regtest/testnet...
                .distinct()
                .forEach(e -> newlyAdded.add(e));*/

        coinsWithValidator.addAll(newlyAdded);

        CurrencyUtil.getAllSortedCryptoCurrencies()
                .forEach(e -> allCryptoCurrencies.add(e.getNameAndCode()));
        StringBuilder sb2 = new StringBuilder("\nAll traded Crypto currencies:\n");
        StringBuilder sb3 = new StringBuilder("\nNever traded Crypto currencies:\n");
        map2.entrySet().stream()
                .sorted((o1, o2) -> Integer.compare(o2.getValue().size(), o1.getValue().size()))
                .forEach(e -> {
                    String key = e.getKey();
                    sb2.append(key).append(": ").append(e.getValue().size()).append("\n");
                    // key is: USD Tether (USDT)
                    String code = key.substring(key.indexOf("(") + 1, key.length() - 1);
                    if (!coinsWithValidator.contains(code) && !newlyAdded.contains(code))
                        allCryptoCurrencies.remove(key);
                });
        log.error(sb2.toString());

        // Not considered age of newly added coins, so take care with removal if coin was added recently.
        allCryptoCurrencies.sort(String::compareTo);
        allCryptoCurrencies
                .forEach(e -> {
                    // key is: USD Tether (USDT)
                    String code = e.substring(e.indexOf("(") + 1, e.length() - 1);
                    if (!coinsWithValidator.contains(code) && !newlyAdded.contains(code))
                        sb3.append(e).append("\n");
                });
        log.error(sb3.toString());
    }
}

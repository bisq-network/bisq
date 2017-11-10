package io.bisq.core.trade.statistics;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bisq.common.UserThread;
import io.bisq.common.locale.CurrencyTuple;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.FileUtil;
import io.bisq.common.storage.JsonFileManager;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class TradeStatisticsManager implements PersistedDataHost {
    private final Storage<TradeStatisticsList> statisticsStorage;
    private final JsonFileManager jsonFileManager;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics> observableTradeStatisticsSet = FXCollections.observableSet();
    private final HashSet<TradeStatistics> tradeStatisticsSet = new HashSet<>();
    private List<TradeStatistics> persistedTradeStatisticsList;

    @Inject
    public TradeStatisticsManager(Storage<TradeStatisticsList> statisticsStorage,
                                  P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(AppOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.statisticsStorage = statisticsStorage;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.dumpStatistics = dumpStatistics;
        jsonFileManager = new JsonFileManager(storageDir);

        this.statisticsStorage.setNumMaxBackupFiles(1);

        // TODO can be removed later. Just to clean up the old PersistedEntryMap and TradeStatisticsList which did
        // not support multi base currencies
        UserThread.runAfter(() -> {
            String pathname = Paths.get(storageDir.getAbsolutePath(), "TradeStatisticsList").toString();
            try {
                FileUtil.deleteFile(new File(pathname));
            } catch (IOException e) {
                e.printStackTrace();
            }
            pathname = Paths.get(storageDir.getAbsolutePath(), "PersistedEntryMap").toString();
            try {
                FileUtil.deleteFile(new File(pathname));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 1);
    }

    @Override
    public void readPersisted() {
        TradeStatisticsList persisted = statisticsStorage.initAndGetPersistedWithFileName("TradeStatistics");
        if (persisted != null)
            persistedTradeStatisticsList = persisted.getList();
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

        if (persistedTradeStatisticsList != null)
            persistedTradeStatisticsList.stream().forEach(e -> add(e, false));

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
                if (protectedStoragePayload instanceof TradeStatistics) {
                    if (BisqEnvironment.getBaseCurrencyNetwork().isBitcoin()) {
                        add((TradeStatistics) protectedStoragePayload, true);
                    } else {
                        // We filter old data items delivered by nodes which still
                        // have 0.5.0 running (we got BTC trade statistic items in v0.5.0)
                        Calendar calendar = Calendar.getInstance();
                        calendar.set(Calendar.DATE, 28);
                        calendar.set(Calendar.MONTH, 5);
                        calendar.set(Calendar.YEAR, 2017);
                        calendar.setTimeZone(TimeZone.getDefault());

                        final TradeStatistics tradeStatistics = (TradeStatistics) protectedStoragePayload;
                        if (tradeStatistics.getTradeDate().after(calendar.getTime()))
                            add(tradeStatistics, true);
                    }
                }
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });

        // At startup the P2PDataStorage inits earlier, otherwise we ge the listener called.
        final List<ProtectedStorageEntry> list = new ArrayList<>(p2PService.getP2PDataStorage().getMap().values());
        list.forEach(e -> {
            final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
            if (protectedStoragePayload instanceof TradeStatistics)
                add((TradeStatistics) protectedStoragePayload, false);

        });

        applyBisqMarketPrice();

        statisticsStorage.queueUpForSave(new TradeStatisticsList(new ArrayList<>(tradeStatisticsSet)), 2000);
        dump();

        // print all currencies sorted by nr. of trades
        // printAllCurrencyStats();

    }

    private void applyBisqMarketPrice() {
        // takes about 10 ms for 5000 items
        Map<String, List<TradeStatistics>> mapByCurrencyCode = new HashMap<>();
        tradeStatisticsSet.stream().forEach(e -> {
            final List<TradeStatistics> list;
            final String currencyCode = e.getCurrencyCode();
            if (mapByCurrencyCode.containsKey(currencyCode)) {
                list = mapByCurrencyCode.get(currencyCode);
            } else {
                list = new ArrayList<>();
                mapByCurrencyCode.put(currencyCode, list);
            }
            list.add(e);
        });

        mapByCurrencyCode.values().stream()
                .filter(list -> !list.isEmpty())
                .forEach(list -> {
                    list.sort((o1, o2) -> o1.getTradeDate().compareTo(o2.getTradeDate()));
                    TradeStatistics tradeStatistics = list.get(list.size() - 1);
                    priceFeedService.setBisqMarketPrice(tradeStatistics.getCurrencyCode(), tradeStatistics.getTradePrice());
                });
    }

    public void add(TradeStatistics tradeStatistics, boolean storeLocally) {
        if (!tradeStatisticsSet.contains(tradeStatistics)) {
            boolean itemAlreadyAdded = tradeStatisticsSet.stream().filter(e -> (e.getOfferId().equals(tradeStatistics.getOfferId()))).findAny().isPresent();
            if (!itemAlreadyAdded) {
                tradeStatisticsSet.add(tradeStatistics);
                observableTradeStatisticsSet.add(tradeStatistics);

                tradeStatistics.getTradePrice().getValue();

                if (storeLocally) {
                    applyBisqMarketPrice();

                    statisticsStorage.queueUpForSave(new TradeStatisticsList(new ArrayList<>(tradeStatisticsSet)), 2000);
                    dump();
                }
            } else {
                log.debug("We have already an item with the same offer ID. That might happen if both the maker and the taker published the tradeStatistics");
            }
        }
    }

    public ObservableSet<TradeStatistics> getObservableTradeStatisticsSet() {
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
            List<TradeStatisticsForJson> list = tradeStatisticsSet.stream().map(TradeStatisticsForJson::new).collect(Collectors.toList());
            list.sort((o1, o2) -> (o1.tradeDate < o2.tradeDate ? 1 : (o1.tradeDate == o2.tradeDate ? 0 : -1)));
            TradeStatisticsForJson[] array = new TradeStatisticsForJson[list.size()];
            list.toArray(array);
            jsonFileManager.writeToDisc(Utilities.objectToJson(array), "trade_statistics");
        }
    }

    private void printAllCurrencyStats() {
        Map<String, Set<TradeStatistics>> map1 = new HashMap<>();
        for (TradeStatistics tradeStatistics : tradeStatisticsSet) {
            if (CurrencyUtil.isFiatCurrency(tradeStatistics.getCounterCurrency())) {
                final String counterCurrency = CurrencyUtil.getNameAndCode(tradeStatistics.getCounterCurrency());
                if (!map1.containsKey(counterCurrency))
                    map1.put(counterCurrency, new HashSet<>());

                map1.get(counterCurrency).add(tradeStatistics);
            }
        }

        StringBuilder sb1 = new StringBuilder("\nAll traded Fiat currencies:\n");
        map1.entrySet().stream()
                .sorted((o1, o2) -> Integer.valueOf(o2.getValue().size()).compareTo(o1.getValue().size()))
                .forEach(e -> sb1.append(e.getKey()).append(": ").append(e.getValue().size()).append("\n"));
        log.error(sb1.toString());

        Map<String, Set<TradeStatistics>> map2 = new HashMap<>();
        for (TradeStatistics tradeStatistics : tradeStatisticsSet) {
            if (CurrencyUtil.isCryptoCurrency(tradeStatistics.getBaseCurrency())) {
                final String code = CurrencyUtil.getNameAndCode(tradeStatistics.getBaseCurrency());
                if (!map2.containsKey(code))
                    map2.put(code, new HashSet<>());

                map2.get(code).add(tradeStatistics);
            }
        }

        List<String> allCryptoCurrencies = new ArrayList<>();
        Set<String> coinsWithValidator = new HashSet<>();
        coinsWithValidator.add("BTC");
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
        coinsWithValidator.add("PNC");
        coinsWithValidator.add("ZEN");
        coinsWithValidator.add("WAC");
        coinsWithValidator.add("DEC");

        // v0.6: DECENT, Pranacoin, WACoins, ZenCash, Ellaism, Cryptonite, Terracoin, Internext
        Set<String> newlyAdded = new HashSet<>();
        newlyAdded.add("DCT");
        newlyAdded.add("PNC");
        newlyAdded.add("WAC");
        newlyAdded.add("ZEN");
        newlyAdded.add("ELLA");
        newlyAdded.add("XCN");
        newlyAdded.add("TRC");
        newlyAdded.add("INXT");

        CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                .forEach(e -> allCryptoCurrencies.add(e.getNameAndCode()));
        StringBuilder sb2 = new StringBuilder("\nAll traded Crypto currencies:\n");
        StringBuilder sb3 = new StringBuilder("\nNever traded Crypto currencies:\n");
        map2.entrySet().stream()
                .sorted((o1, o2) -> Integer.valueOf(o2.getValue().size()).compareTo(o1.getValue().size()))
                .forEach(e -> {
                    final String key = e.getKey();
                    sb2.append(key).append(": ").append(e.getValue().size()).append("\n");
                    // key is: USD Tether (USDT)
                    String code = key.substring(key.indexOf("(") + 1, key.length() - 1);
                    if (!coinsWithValidator.contains(code) && !newlyAdded.contains(code))
                        allCryptoCurrencies.remove(key);
                });
        log.error(sb2.toString());

        // Not considered age of newly added coins, so take care with removal if coin was added recently.
        allCryptoCurrencies.sort(String::compareTo);
        allCryptoCurrencies.stream()
                .forEach(e -> {
                    // key is: USD Tether (USDT)
                    String code = e.substring(e.indexOf("(") + 1, e.length() - 1);
                    if (!coinsWithValidator.contains(code) && !newlyAdded.contains(code))
                        sb3.append(e).append("\n");
                });
        log.error(sb3.toString());
    }
}

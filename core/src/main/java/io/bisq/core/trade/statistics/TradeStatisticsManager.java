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
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.StoragePayload;
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
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics> observableTradeStatisticsSet = FXCollections.observableSet();
    private final HashSet<TradeStatistics> tradeStatisticsSet = new HashSet<>();
    private List<TradeStatistics> persistedTradeStatisticsList;

    @Inject
    public TradeStatisticsManager(Storage<TradeStatisticsList> statisticsStorage,
                                  P2PService p2PService,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(AppOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.statisticsStorage = statisticsStorage;
        this.p2PService = p2PService;
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
                final StoragePayload storagePayload = data.getStoragePayload();
                if (storagePayload instanceof TradeStatistics) {
                    if (BisqEnvironment.getBaseCurrencyNetwork().isBitcoin()) {
                        add((TradeStatistics) storagePayload, true);
                    } else {
                        // We filter old data items delivered by nodes which still 
                        // have 0.5.0 running (we got BTC trade statistic items in v0.5.0)
                        Calendar calendar = Calendar.getInstance();
                        calendar.set(Calendar.DATE, 28);
                        calendar.set(Calendar.MONTH, 5);
                        calendar.set(Calendar.YEAR, 2017);
                        calendar.setTimeZone(TimeZone.getDefault());

                        final TradeStatistics tradeStatistics = (TradeStatistics) storagePayload;
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
            final StoragePayload storagePayload = e.getStoragePayload();
            if (storagePayload instanceof TradeStatistics)
                add((TradeStatistics) storagePayload, false);

        });

        statisticsStorage.queueUpForSave(new TradeStatisticsList(new ArrayList<>(tradeStatisticsSet)), 2000);
        dump();

        // print all currencies sorted by nr. of trades
        // printAllCurrencyStats();

    }

    public void add(TradeStatistics tradeStatistics, boolean storeLocally) {
        if (!tradeStatisticsSet.contains(tradeStatistics)) {
            boolean itemAlreadyAdded = tradeStatisticsSet.stream().filter(e -> (e.getOfferId().equals(tradeStatistics.getOfferId()))).findAny().isPresent();
            if (!itemAlreadyAdded) {
                tradeStatisticsSet.add(tradeStatistics);
                observableTradeStatisticsSet.add(tradeStatistics);

                if (storeLocally) {
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

        StringBuilder sb1 = new StringBuilder();
        map1.entrySet().stream()
                .sorted((o1, o2) -> Integer.valueOf(o2.getValue().size()).compareTo(o1.getValue().size()))
                .forEach(e -> sb1.append(e.getKey()).append(": ").append(e.getValue().size()).append("\n"));
        log.error(sb1.toString());

        Map<String, Set<TradeStatistics>> map2 = new HashMap<>();
        for (TradeStatistics tradeStatistics : tradeStatisticsSet) {
            if (CurrencyUtil.isCryptoCurrency(tradeStatistics.getCounterCurrency())) {
                final String counterCurrency = CurrencyUtil.getNameAndCode(tradeStatistics.getCounterCurrency());
                if (!map2.containsKey(counterCurrency))
                    map2.put(counterCurrency, new HashSet<>());

                map2.get(counterCurrency).add(tradeStatistics);
            }
        }

        StringBuilder sb2 = new StringBuilder();
        map2.entrySet().stream()
                .sorted((o1, o2) -> Integer.valueOf(o2.getValue().size()).compareTo(o1.getValue().size()))
                .forEach(e -> sb2.append(e.getKey()).append(": ").append(e.getValue().size()).append("\n"));
        log.error(sb2.toString());
    }
}

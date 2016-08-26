package io.bitsquare.trade.statistics;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bitsquare.app.CoreOptionKeys;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.locale.CurrencyTuple;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bitsquare.storage.PlainTextWrapper;
import io.bitsquare.storage.Storage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class TradeStatisticsManager {
    private static final Logger log = LoggerFactory.getLogger(TradeStatisticsManager.class);

    private final Storage<HashSet<TradeStatistics>> statisticsStorage;
    private Storage<PlainTextWrapper> fiatCurrencyListJsonStorage;
    private Storage<PlainTextWrapper> cryptoCurrencyListJsonStorage;
    private Storage<PlainTextWrapper> statisticsJsonStorage;
    private boolean dumpStatistics;
    private ObservableSet<TradeStatistics> observableTradeStatisticsSet = FXCollections.observableSet();
    private HashSet<TradeStatistics> tradeStatisticsSet = new HashSet<>();

    @Inject
    public TradeStatisticsManager(Storage<HashSet<TradeStatistics>> statisticsStorage,
                                  Storage<PlainTextWrapper> fiatCurrencyListJsonStorage,
                                  Storage<PlainTextWrapper> cryptoCurrencyListJsonStorage,
                                  Storage<PlainTextWrapper> statisticsJsonStorage,
                                  P2PService p2PService,
                                  @Named(CoreOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.statisticsStorage = statisticsStorage;
        this.fiatCurrencyListJsonStorage = fiatCurrencyListJsonStorage;
        this.cryptoCurrencyListJsonStorage = cryptoCurrencyListJsonStorage;
        this.statisticsJsonStorage = statisticsJsonStorage;
        this.dumpStatistics = dumpStatistics;

        init(p2PService);
    }

    private void init(P2PService p2PService) {
        if (dumpStatistics) {
            this.statisticsJsonStorage.initWithFileName("trade_statistics.json");

            this.fiatCurrencyListJsonStorage.initWithFileName("fiat_currency_list.json");
            ArrayList<CurrencyTuple> fiatCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            fiatCurrencyListJsonStorage.queueUpForSave(new PlainTextWrapper(Utilities.objectToJson(fiatCurrencyList)), 2000);

            this.cryptoCurrencyListJsonStorage.initWithFileName("crypto_currency_list.json");
            ArrayList<CurrencyTuple> cryptoCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            cryptoCurrencyList.add(0, new CurrencyTuple("BTC", "Bitcoin", 8));
            cryptoCurrencyListJsonStorage.queueUpForSave(new PlainTextWrapper(Utilities.objectToJson(cryptoCurrencyList)), 2000);
        }

        HashSet<TradeStatistics> persisted = statisticsStorage.initAndGetPersistedWithFileName("TradeStatistics");
        if (persisted != null)
            persisted.stream().forEach(this::add);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final StoragePayload storagePayload = data.getStoragePayload();
                if (storagePayload instanceof TradeStatistics)
                    add((TradeStatistics) storagePayload);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });

        // At startup the P2PDataStorage inits earlier, otherwise we ge the listener called.
        p2PService.getP2PDataStorage().getMap().values().forEach(e -> {
            final StoragePayload storagePayload = e.getStoragePayload();
            if (storagePayload instanceof TradeStatistics)
                add((TradeStatistics) storagePayload);
        });
    }

    public void add(TradeStatistics tradeStatistics) {
        if (!tradeStatisticsSet.contains(tradeStatistics)) {
            boolean itemAlreadyAdded = tradeStatisticsSet.stream().filter(e -> (e.offerId.equals(tradeStatistics.offerId))).findAny().isPresent();
            if (!itemAlreadyAdded) {
                tradeStatisticsSet.add(tradeStatistics);
                observableTradeStatisticsSet.add(tradeStatistics);
                statisticsStorage.queueUpForSave(new HashSet<>(tradeStatisticsSet), 2000);

                dump();
            } else {
                log.debug("We have already an item with the same offer ID. That might happen if both the offerer and the taker published the tradeStatistics");
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
            statisticsJsonStorage.queueUpForSave(new PlainTextWrapper(Utilities.objectToJson(array)), 5000);
        }
    }
}

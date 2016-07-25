package io.bitsquare.trade;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bitsquare.app.CoreOptionKeys;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bitsquare.storage.Storage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class TradeStatisticsManager {
    private static final Logger log = LoggerFactory.getLogger(TradeStatisticsManager.class);
    private final Storage<HashSet<TradeStatistics>> storage;
    private Storage<String> jsonStorage;
    private boolean dumpStatistics;

    private ObservableSet<TradeStatistics> observableTradeStatisticsSet = FXCollections.observableSet();
    private HashSet<TradeStatistics> tradeStatisticsSet = new HashSet<>();

    @Inject
    public TradeStatisticsManager(Storage<HashSet<TradeStatistics>> storage, Storage<String> jsonStorage, P2PService p2PService, @Named(CoreOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.storage = storage;
        this.jsonStorage = jsonStorage;
        this.dumpStatistics = dumpStatistics;

        if (dumpStatistics)
            this.jsonStorage.initAndGetPersistedWithFileName("trade_statistics.json");

        HashSet<TradeStatistics> persisted = storage.initAndGetPersistedWithFileName("TradeStatistics");
        if (persisted != null)
            observableTradeStatisticsSet = FXCollections.observableSet(persisted);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final StoragePayload storagePayload = data.getStoragePayload();
                if (storagePayload instanceof TradeStatistics) {
                    add((TradeStatistics) storagePayload);
                }
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });
    }

    public void add(TradeStatistics tradeStatistics) {
        if (!observableTradeStatisticsSet.contains(tradeStatistics)) {
            observableTradeStatisticsSet.add(tradeStatistics);
            tradeStatisticsSet.add(tradeStatistics);
            storage.queueUpForSave(tradeStatisticsSet, 2000);

            if (dumpStatistics) {
                // We store the statistics as json so it is easy for further processing (e.g. for web based services)
                // TODO This is just a quick solution for storing to one file. 
                // 1 statistic entry has 500 bytes as json.
                // Need a more scalable solution later when we get more volume.
                // The flag will only be activated by dedicated nodes, so it should not be too critical for the moment, but needs to
                // get improved. Maybe a LevelDB like DB...? Could be impl. in a headless version only.
                List<TradeStatistics> list = tradeStatisticsSet.stream().collect(Collectors.toList());
                list.sort((o1, o2) -> (o1.tradeDate < o2.tradeDate ? 1 : (o1.tradeDate == o2.tradeDate ? 0 : -1)));
                TradeStatistics[] array = new TradeStatistics[tradeStatisticsSet.size()];
                list.toArray(array);
                jsonStorage.queueUpForSave(Utilities.objectToJson(array), 5_000);
            }
        }
    }

    public ObservableSet<TradeStatistics> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }
}

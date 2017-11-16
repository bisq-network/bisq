package io.bisq.core.trade.statistics;

import com.google.inject.name.Named;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.FileUtil;
import io.bisq.common.storage.ResourceNotFoundException;
import io.bisq.common.storage.Storage;
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.network.p2p.storage.PersistableEntryMap;
import io.bisq.network.p2p.storage.PersistableNetworkPayloadCollection;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;

//migrate old trade statistics (TradeStatistics) from EntryMap_BTC_MAINNET to PersistableNetworkPayloadMap (TradeStatistics2)
@Slf4j
public class TradeStatisticsMigrationTool {

    private final Storage<PersistableNetworkPayloadCollection> persistableNetworkPayloadMapStorage;
    private PersistableNetworkPayloadCollection persistableNetworkPayloadCollection;

    @Inject
    public TradeStatisticsMigrationTool(PersistenceProtoResolver persistenceProtoResolver,
                                        @Named(Storage.STORAGE_DIR) File storageDir) {

        persistableNetworkPayloadMapStorage = new Storage<>(storageDir, persistenceProtoResolver);
        String storageFileName = "PersistableNetworkPayloadMap";
        persistableNetworkPayloadCollection = persistableNetworkPayloadMapStorage.initAndGetPersistedWithFileName(storageFileName, 100);
        persistableNetworkPayloadCollection = new PersistableNetworkPayloadCollection();

        Storage<PersistableEntryMap> persistedEntryMapStorage = new Storage<>(storageDir, persistenceProtoResolver);
        persistedEntryMapStorage.setNumMaxBackupFiles(1);

        storageFileName = "EntryMap";
        String resourceFileName = "EntryMap";
        File dbDir = new File(storageDir.getAbsolutePath());
        if (!dbDir.exists() && !dbDir.mkdir())
            log.warn("make dir failed.\ndbDir=" + dbDir.getAbsolutePath());

        File destinationFile = new File(Paths.get(storageDir.getAbsolutePath(), storageFileName).toString());
        if (!destinationFile.exists()) {
            try {
                log.info("We copy resource to file: resourceFileName={}, destinationFile={}", resourceFileName, destinationFile);
                FileUtil.resourceToFile(resourceFileName, destinationFile);
            } catch (ResourceNotFoundException e) {
                log.info("Could not find resourceFile " + resourceFileName + ". That is expected if none is provided yet.");
            } catch (Throwable e) {
                log.error("Could not copy resourceFile " + resourceFileName + " to " +
                        destinationFile.getAbsolutePath() + ".\n" + e.getMessage());
                e.printStackTrace();
            }
        } else {
            log.debug(storageFileName + " file exists already.");
        }
        PersistableEntryMap persistedEntryMap = persistedEntryMapStorage.<HashMap<P2PDataStorage.ByteArray, P2PDataStorage.MapValue>>initAndGetPersistedWithFileName(storageFileName, 100);

        log.error("persistedEntryMap.getMap().values() " + persistedEntryMap.getMap().values().size());
        persistedEntryMap.getMap().values().stream()
                .filter(e -> e.getProtectedStoragePayload() instanceof TradeStatistics)
                .forEach(e -> {
                    TradeStatistics2 stat = TradeStatisticsManager.ConvertToTradeStatistics2((TradeStatistics) e.getProtectedStoragePayload());
                    persistableNetworkPayloadCollection.getMap().putIfAbsent(new P2PDataStorage.ByteArray(stat.getHash()), stat);
                });

        log.error("persistableNetworkPayloadCollection " + persistableNetworkPayloadCollection.getMap().size());
        persistableNetworkPayloadMapStorage.queueUpForSave(persistableNetworkPayloadCollection, 100);
    }
}

package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Version;
import bisq.common.storage.FileUtil;
import bisq.common.storage.ResourceNotFoundException;
import bisq.common.storage.Storage;

import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages historical data stores tagged with the release versions. Those stores are immutable data. New data is added
 * to the default map in the store (live data). The historical data are used when the client requests the full data set.
 * For initial data requests we only use the live data as the version is sent with the request so the responding node
 * can figure out if we miss any of the historical data.
 */
@Slf4j
public abstract class SplitStoreService<T extends PersistableNetworkPayloadStore> extends MapStoreService<T, PersistableNetworkPayload> {
    private final Map<String, PersistableNetworkPayloadStore> storesByVersion = new HashMap<>();
    private final Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> historicalDataMap = new HashMap<>();

    public SplitStoreService(File storageDir, Storage<T> storage) {
        super(storageDir, storage);
    }

    @Override
    protected void put(P2PDataStorage.ByteArray hash, PersistableNetworkPayload payload) {
        // make sure we do not add data that we already have (in a bin of historical data)
        if (getMap().containsKey(hash)) {
            return;
        }

        store.getMap().put(hash, payload);
        persist();
    }

    @Override
    protected PersistableNetworkPayload putIfAbsent(P2PDataStorage.ByteArray hash,
                                                    PersistableNetworkPayload payload) {
        // make sure we do not add data that we already have (in a bin of historical data)
        if (getMap().containsKey(hash)) {
            return null;
        }

        PersistableNetworkPayload previous = store.getMap().put(hash, payload);
        persist();
        return previous;
    }

    /**
     * @return Map of our live store merged with the historical stores
     */

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap(boolean ignoreHistoricalData) {
        if (ignoreHistoricalData) {
            return store.getMap();
        } else {
            // We merge the historical data with our live map
            Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mergedMap = new HashMap<>(store.getMap());
            mergedMap.putAll(historicalDataMap);
            return mergedMap;
        }
    }

    // By default we want to get all data including he historical data
    @Override
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        return getMap(false);
    }

    /**
     * @return Map of our live store merged with the historical stores which are newer than the verion parameter
     */
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap(String version) {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mergedMap = new HashMap<>(store.getMap());
        storesByVersion.entrySet().stream()
                .filter(entry -> {
                    int storeVersion = Integer.parseInt(entry.getKey().replace(".", ""));
                    int requestersVersion = Integer.parseInt(version);
                    return storeVersion > requestersVersion;
                })
                .map(e -> e.getValue().getMap())
                .forEach(mergedMap::putAll);
        return mergedMap;
    }

    /**
     * For the {@link SplitStoreService}s, we check if we already have all the historical data stores in our db
     * directory. If we have, we can proceed loading the stores. If we do not, we have to create the stores
     * from resources.
     *
     * @param postFix The post fix indicating the network and coin (was used in the past when multiple base currencies was supported)
     */
    @Override
    protected void readFromResources(String postFix) {
        readStore();

        List<String> versions = new ArrayList<>(Version.history);
        versions.forEach(version -> {
            String versionedFileName = getFileName() + "_" + version;
            File versionedFile = new File(absolutePathOfStorageDir, versionedFileName);
            if (versionedFile.exists()) {
                T versionedStore = getStore(versionedFileName);
                storesByVersion.put(version, versionedStore);
                historicalDataMap.putAll(versionedStore.getMap());
            } else {
                PersistableNetworkPayloadStore storeFromResource = getStoreFromResource(version, postFix);
                pruneStore(storeFromResource);
                storesByVersion.put(version, storeFromResource);
                historicalDataMap.putAll(storeFromResource.getMap());
            }
        });
    }

    /**
     * Creating a file from resources
     *
     * @param version to identify the data store eg. "1.3.4"
     * @param postFix the global postfix eg. "_BTC_MAINNET"
     * @return The store created from resource file
     */
    private PersistableNetworkPayloadStore getStoreFromResource(String version, String postFix) {
        // if not, copy and split
        String versionedFileName = getFileName() + "_" + version;
        File destinationFile = new File(absolutePathOfStorageDir, versionedFileName);
        String resourceFileName = versionedFileName + postFix; // postFix has a preceding "_" already
        try {
            log.info("We copy resource to file: resourceFileName={}, destinationFile={}", resourceFileName, destinationFile);
            FileUtil.resourceToFile(resourceFileName, destinationFile);
        } catch (ResourceNotFoundException e) {
            log.info("Could not find resourceFile {}. That is expected if none is provided yet.", resourceFileName);
        } catch (Throwable e) {
            log.error("Could not copy resourceFile {} to {}.\n{}", resourceFileName, destinationFile.getAbsolutePath(), e.getMessage());
            e.printStackTrace();
        }

        return getStore(versionedFileName);
    }

    /**
     * Removes entries in our live store which exists in the historical store already
     *
     * @param historicalStore   The historical store we use for pruning
     */
    private void pruneStore(PersistableNetworkPayloadStore historicalStore) {
        store.getMap().keySet().removeAll(historicalStore.getMap().keySet());

        storage.queueUpForSave();
    }
}

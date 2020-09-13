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
 * Has business logic to operate data stores which are spread across multiple files.<br><br><p>
 *
 * <h3>Use Case</h3>
 * Startup requires to send all object keys to the network in order to get the new data.
 * However, data stores got quite big over time and will grow even faster in the future.
 * With multi-file data stores, we can query for new objects since the last snapshot (ie.
 * the last release) and subsequently send "I am Bisq vx.y.z and I have these new objects"
 * to the network and the network can respond accordingly.</p><br><br><p>
 *
 * <h3>Features</h3>
 * In order to only get a specific part of all the objects available in the complete data
 * store, the original single-file data store had to be split up and this class and
 * {@link SplitStore} are there to handle the business logic needed for
 * <ul>
 * <li>migrating to the new and shiny multi-file data store</li>
 * <li>shoveling around data in case Bisq gets updated to a new version</li>
 * <li>takes care of setting up a fresh Bisq install</li>
 * <li>makes sure that historical data cannot be altered easily</li>
 * <li>adding data to the store will only amend the live store</li>
 * <li>takes care of keeping the legacy API functional</li>
 * <li>adds the feature of filtering object queries by Bisq release</li>
 * </ul>
 * </p>
 *
 * <h3>Further reading</h3><p><ul>
 *     <li><a href="https://github.com/bisq-network/projects/issues/25">project description</a></li>
 * </ul></p>
 */
@Slf4j
public abstract class SplitStoreService<T extends SplitStore> extends MapStoreService<T, PersistableNetworkPayload> {
    private final Map<String, SplitStore> history = new HashMap<>();
    private final Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapOfHistoricalStores = new HashMap<>();

    public SplitStoreService(File storageDir, Storage<T> storage) {
        super(storageDir, storage);
    }

    @Override
    protected void put(P2PDataStorage.ByteArray hash, PersistableNetworkPayload payload) {
        // make sure we do not add data that we already have (in a bin of historical data)
        if (getMap().containsKey(hash))
            return;

        store.getMap().put(hash, payload);
        persist();
    }

    @Override
    protected PersistableNetworkPayload putIfAbsent(P2PDataStorage.ByteArray hash,
                                                    PersistableNetworkPayload payload) {
        // make sure we do not add data that we already have (in a bin of historical data)
        if (getMap().containsKey(hash))
            return null;

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
            mergedMap.putAll(mapOfHistoricalStores);
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
        history.entrySet().stream()
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
                history.put(version, versionedStore);
                mapOfHistoricalStores.putAll(versionedStore.getMap());
            } else {
                SplitStore storeFromResource = getStoreFromResource(version, postFix);
                pruneStore(storeFromResource);
                history.put(version, storeFromResource);
                mapOfHistoricalStores.putAll(storeFromResource.getMap());
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
    private SplitStore getStoreFromResource(String version, String postFix) {
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
    private void pruneStore(SplitStore historicalStore) {
        store.getMap().keySet().removeAll(historicalStore.getMap().keySet());

        storage.queueUpForSave();
    }
}

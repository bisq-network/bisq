package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Version;
import bisq.common.storage.Storage;

import com.google.common.collect.ImmutableMap;

import java.io.File;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages historical data stores tagged with the release versions.
 * New data is added to the default map in the store (live data). Historical data is created from resource files.
 * For initial data requests we only use the live data as the users version is sent with the
 * request so the responding (seed)node can figure out if we miss any of the historical data.
 */
@Slf4j
public abstract class HistoricalDataStoreService<T extends PersistableNetworkPayloadStore> extends MapStoreService<T, PersistableNetworkPayload> {
    private ImmutableMap<String, PersistableNetworkPayloadStore> storesByVersion;
    // Cache to avoid that we have to recreate the historical data at each request
    private ImmutableMap<P2PDataStorage.ByteArray, PersistableNetworkPayload> allHistoricalPayloads;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public HistoricalDataStoreService(File storageDir, Storage<T> storage) {
        super(storageDir, storage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We give back a map of our live map and all historical maps newer than the requested version.
    // If requestersVersion is null we return all historical data.
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMapSinceVersion(String requestersVersion) {
        // We add all our live data
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> result = new HashMap<>(store.getMap());

        // If we have a store with a newer version than the requesters version we will add those as well.
        storesByVersion.entrySet().stream()
                .filter(entry -> {
                    // Old nodes not sending the version will get delivered all data
                    if (requestersVersion == null) {
                        log.info("The requester did not send a version. This is expected for not updated nodes.");
                        return true;
                    }

                    // Otherwise we only add data if the requesters version is older then
                    // the version of the particular store.
                    String storeVersion = entry.getKey();
                    boolean newVersion = Version.isNewVersion(storeVersion, requestersVersion);
                    String details = newVersion ?
                            "As our historical store is a newer version we add the data to our result map." :
                            "As the requester version is not older as our historical store we do not " +
                                    "add the data to the result map.";
                    log.info("The requester had version {}. Our historical data store has version {}.\n{}",
                            requestersVersion, storeVersion, details);
                    return newVersion;
                })
                .map(e -> e.getValue().getMap())
                .forEach(result::putAll);

        log.info("We found {} entries since requesters version {}",
                result.size(), requestersVersion);
        return result;
    }

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMapOfLiveData() {
        return store.getMap();
    }

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMapOfAllData() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> result = new HashMap<>(getMapOfLiveData());
        result.putAll(allHistoricalPayloads);
        return result;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MapStoreService
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO optimize so that callers to AppendOnlyDataStoreService are not invoking that often getMap
    // ProposalService is one of the main callers and could avoid it by using the ProposalStoreService directly
    // instead of AppendOnlyDataStoreService

    // By default we return the live data only. This method should not be used by domain clients but rather the
    // custom methods getMapOfAllData, getMapOfLiveData or getMapSinceVersion
    @Override
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        return store.getMap();
    }

    @Override
    protected void put(P2PDataStorage.ByteArray hash, PersistableNetworkPayload payload) {
        if (anyMapContainsKey(hash)) {
            return;
        }

        getMapOfLiveData().put(hash, payload);
        persist();
    }

    @Override
    protected PersistableNetworkPayload putIfAbsent(P2PDataStorage.ByteArray hash, PersistableNetworkPayload payload) {
        if (anyMapContainsKey(hash)) {
            return null;
        }

        PersistableNetworkPayload previous = getMapOfLiveData().put(hash, payload);
        persist();
        return previous;
    }


    @Override
    protected void readFromResources(String postFix) {
        readStore();
        log.info("We have created the {} store for the live data and filled it with {} entries from the persisted data.",
                getFileName(), getMapOfLiveData().size());

        // Now we add our historical data stores. As they are immutable after created we use an ImmutableMap
        ImmutableMap.Builder<P2PDataStorage.ByteArray, PersistableNetworkPayload> allHistoricalPayloadsBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<String, PersistableNetworkPayloadStore> storesByVersionBuilder = ImmutableMap.builder();

        Version.HISTORY.forEach(version -> readHistoricalStoreFromResources(version, postFix, allHistoricalPayloadsBuilder, storesByVersionBuilder));

        allHistoricalPayloads = allHistoricalPayloadsBuilder.build();
        storesByVersion = storesByVersionBuilder.build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void readHistoricalStoreFromResources(String version,
                                                  String postFix,
                                                  ImmutableMap.Builder<P2PDataStorage.ByteArray, PersistableNetworkPayload> allHistoricalDataBuilder,
                                                  ImmutableMap.Builder<String, PersistableNetworkPayloadStore> storesByVersionBuilder) {
        String fileName = getFileName() + "_" + version;
        boolean wasCreatedFromResources = makeFileFromResourceFile(fileName, postFix);

        // If resource file does not exist we return null. We do not create a new store as it would never get filled.
        PersistableNetworkPayloadStore historicalStore = storage.getPersisted(fileName);
        if (historicalStore == null) {
            log.warn("Resource file with file name {} does not exits.", fileName);
            return;
        }

        storesByVersionBuilder.put(version, historicalStore);
        allHistoricalDataBuilder.putAll(historicalStore.getMap());

        if (wasCreatedFromResources) {
            pruneStore(historicalStore, version);
        }
    }

    private void pruneStore(PersistableNetworkPayloadStore historicalStore, String version) {
        int preLive = getMapOfLiveData().keySet().size();
        getMapOfLiveData().keySet().removeAll(historicalStore.getMap().keySet());
        int postLive = getMapOfLiveData().size();
        if (preLive > postLive) {
            log.info("We pruned data from our live data store which are already contained in the historical data store with version {}. " +
                            "The live map had {} entries before pruning and has {} entries afterwards.",
                    version, preLive, postLive);
        } else {
            log.info("No pruning from historical data store with version {} was applied", version);
        }
        storage.queueUpForSave(store);
    }

    private boolean anyMapContainsKey(P2PDataStorage.ByteArray hash) {
        return getMapOfLiveData().containsKey(hash) || allHistoricalPayloads.containsKey(hash);
    }
}

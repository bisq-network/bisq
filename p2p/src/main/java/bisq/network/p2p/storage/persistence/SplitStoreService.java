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
 * For initial data requests we only use the live data as the version is sent with the
 * request so the responding (seed)node can figure out if we miss any of the historical data.
 */
@Slf4j
public abstract class SplitStoreService<T extends PersistableNetworkPayloadStore> extends MapStoreService<T, PersistableNetworkPayload> {
    private ImmutableMap<String, PersistableNetworkPayloadStore> storesByVersion;
    private ImmutableMap<P2PDataStorage.ByteArray, PersistableNetworkPayload> allHistoricalPayloads;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////
    public SplitStoreService(File storageDir, Storage<T> storage) {
        super(storageDir, storage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We give back a map of our live map and all historical maps newer than the requested version.
    // If requestersVersion is null we return all historical data.
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMapSinceVersion(String requestersVersion) {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> result = new HashMap<>(store.getMap());
        storesByVersion.entrySet().stream()
                .filter(entry -> {
                    if (requestersVersion == null) {
                        return true;
                    }

                    String storeVersion = entry.getKey();
                    return Version.isNewVersion(storeVersion, requestersVersion);
                })
                .map(e -> e.getValue().getMap())
                .forEach(result::putAll);
        return result;
    }

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMapOfLiveData() {
        return store.getMap();
    }

    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMapOfAllData() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> result = new HashMap<>(store.getMap());
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

        store.getMap().put(hash, payload);
        persist();
    }

    @Override
    protected PersistableNetworkPayload putIfAbsent(P2PDataStorage.ByteArray hash, PersistableNetworkPayload payload) {
        if (anyMapContainsKey(hash)) {
            return null;
        }

        PersistableNetworkPayload previous = store.getMap().put(hash, payload);
        persist();
        return previous;
    }


    @Override
    protected void readFromResources(String postFix) {
        // We create the store for the live data
        super.readFromResources(postFix);

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
        makeFileFromResourceFile(fileName, postFix);

        // If resource file does not exist we return null. We do not create a new store as it would never get filled.
        PersistableNetworkPayloadStore historicalStore = storage.getPersisted(fileName);
        if (historicalStore == null) {
            return;
        }

        storesByVersionBuilder.put(version, historicalStore);
        allHistoricalDataBuilder.putAll(historicalStore.getMap());

        pruneStore(historicalStore);
    }

    private void pruneStore(PersistableNetworkPayloadStore historicalStore) {
        store.getMap().keySet().removeAll(historicalStore.getMap().keySet());
        storage.queueUpForSave(store);
    }

    private boolean anyMapContainsKey(P2PDataStorage.ByteArray hash) {
        return store.getMap().containsKey(hash) || allHistoricalPayloads.containsKey(hash);
    }
}

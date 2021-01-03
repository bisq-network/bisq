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

package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.persistence.PersistenceManager;

import com.google.common.collect.ImmutableMap;

import java.io.File;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages historical data stores tagged with the release versions.
 * New data is added to the default map in the store (live data). Historical data is created from resource files.
 * For initial data requests we only use the live data as the users version is sent with the
 * request so the responding (seed)node can figure out if we miss any of the historical data.
 */
@Slf4j
public abstract class HistoricalDataStoreService<T extends PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> extends MapStoreService<T, PersistableNetworkPayload> {
    private ImmutableMap<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storesByVersion;
    // Cache to avoid that we have to recreate the historical data at each request
    private ImmutableMap<P2PDataStorage.ByteArray, PersistableNetworkPayload> allHistoricalPayloads;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public HistoricalDataStoreService(File storageDir, PersistenceManager<T> persistenceManager) {
        super(storageDir, persistenceManager);
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

    @Override
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        DevEnv.logErrorAndThrowIfDevMode("HistoricalDataStoreService.getMap should not be used by domain " +
                "clients but rather the custom methods getMapOfAllData, getMapOfLiveData or getMapSinceVersion");
        return getMapOfAllData();
    }

    @Override
    protected void put(P2PDataStorage.ByteArray hash, PersistableNetworkPayload payload) {
        if (anyMapContainsKey(hash)) {
            return;
        }

        getMapOfLiveData().put(hash, payload);
        requestPersistence();
    }

    @Override
    protected PersistableNetworkPayload putIfAbsent(P2PDataStorage.ByteArray hash, PersistableNetworkPayload payload) {
        if (anyMapContainsKey(hash)) {
            return null;
        }

        // We do not return the value from getMapOfLiveData().put as we checked before that it does not contain any value.
        // So it will be always null. We still keep the return type as we override the method from MapStoreService which
        // follow the Map.putIfAbsent signature.
        getMapOfLiveData().put(hash, payload);
        requestPersistence();
        return null;
    }


    @Override
    protected void readFromResources(String postFix, Runnable completeHandler) {
        readStore(persisted -> {
            log.info("We have created the {} store for the live data and filled it with {} entries from the persisted data.",
                    getFileName(), getMapOfLiveData().size());

            // Now we add our historical data stores.
            Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> allHistoricalPayloads = new HashMap<>();
            Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storesByVersion = new HashMap<>();
            AtomicInteger numFiles = new AtomicInteger(Version.HISTORICAL_RESOURCE_FILE_VERSION_TAGS.size());
            Version.HISTORICAL_RESOURCE_FILE_VERSION_TAGS.forEach(version -> readHistoricalStoreFromResources(version,
                    postFix,
                    allHistoricalPayloads,
                    storesByVersion,
                    () -> {
                        if (numFiles.decrementAndGet() == 0) {
                            // At last iteration we set the immutable map
                            this.allHistoricalPayloads = ImmutableMap.copyOf(allHistoricalPayloads);
                            this.storesByVersion = ImmutableMap.copyOf(storesByVersion);
                            completeHandler.run();
                        }
                    }));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void readHistoricalStoreFromResources(String version,
                                                  String postFix,
                                                  Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> allHistoricalPayloads,
                                                  Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storesByVersion,
                                                  Runnable completeHandler) {

        String fileName = getFileName() + "_" + version;
        boolean wasCreatedFromResources = makeFileFromResourceFile(fileName, postFix);

        // If resource file does not exist we do not create a new store as it would never get filled.
        persistenceManager.readPersisted(fileName, persisted -> {
                    storesByVersion.put(version, persisted);
                    allHistoricalPayloads.putAll(persisted.getMap());
                    log.info("We have read from {} {} historical items.", fileName, persisted.getMap().size());
                    pruneStore(persisted, version);
                    completeHandler.run();
                },
                completeHandler::run);
    }

    private void pruneStore(PersistableNetworkPayloadStore<? extends PersistableNetworkPayload> historicalStore,
                            String version) {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapOfLiveData = getMapOfLiveData();
        int preLive = mapOfLiveData.size();
        mapOfLiveData.keySet().removeAll(historicalStore.getMap().keySet());
        int postLive = mapOfLiveData.size();
        if (preLive > postLive) {
            log.info("We pruned data from our live data store which are already contained in the historical data store with version {}. " +
                            "The live map had {} entries before pruning and has {} entries afterwards.",
                    version, preLive, postLive);
        } else {
            log.info("No pruning from historical data store with version {} was applied", version);
        }
        requestPersistence();
    }

    private boolean anyMapContainsKey(P2PDataStorage.ByteArray hash) {
        return getMapOfLiveData().containsKey(hash) || allHistoricalPayloads.containsKey(hash);
    }
}

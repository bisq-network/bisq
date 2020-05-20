package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Version;
import bisq.common.storage.FileUtil;
import bisq.common.storage.ResourceNotFoundException;
import bisq.common.storage.Storage;

import java.nio.file.Paths;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    protected HashMap<String, SplitStore> history;

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

        PersistableNetworkPayload previous = store.getMap().putIfAbsent(hash, payload);
        persist();
        return previous;
    }


    @Override
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        HashMap<P2PDataStorage.ByteArray, PersistableNetworkPayload> result = new HashMap<>();
        result.putAll(store.getMap());
        history.forEach((s, store) -> result.putAll(store.getMap()));

        return result;
    }

    @Override
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap(String filter) {
        HashMap<P2PDataStorage.ByteArray, PersistableNetworkPayload> result = new HashMap<>();
        result.putAll(store.getMap());

        // TODO do a proper language, possibly classes
        if (filter.startsWith("since ")) {
            String finalFilter = filter.replace("since ", "");
            if (!finalFilter.equals(Version.VERSION)) {
                history.entrySet().stream()
                        .filter(entry -> parseSpecialKey(entry.getKey()) > parseSpecialKey(finalFilter))
                        .forEach(entry -> result.putAll(entry.getValue().getMap()));
            }
        }

        return result;
    }

    private int parseSpecialKey(String specialKey) {
        return Integer.parseInt(specialKey.replace(".", ""));
    }

    /**
     * For the {@link SplitStoreService}s, we check if we already have all the historical data stores in our working
     * directory. If we have, we can proceed loading the stores. If we do not, we have to transfer the fresh data stores
     * from resources.
     *
     * @param postFix
     */
    @Override
    protected void readFromResources(String postFix) {
        // check Version.VERSION and see if we have latest r/o data store file in working directory
        if (!new File(absolutePathOfStorageDir, getFileName() + "_" + Version.VERSION).exists())
            makeFileFromResourceFile(postFix); // if we have the latest file, we are good, else do stuff // TODO are we?
        else {
            // if we have the r/o data stores in our working directory already, we can proceed on loading them.
            File dbDir = new File(absolutePathOfStorageDir);
            List<File> resourceFiles = Arrays.asList(dbDir.list((dir, name) -> name.startsWith(getFileName() + "_")))
                    .stream()
                    .map(s -> new File(s))
                    .collect(Collectors.toList());

            history = new HashMap<>();
            store = readStore(getFileName());
            resourceFiles.forEach(file -> {
                SplitStore tmp = readStore(file.getName().replace(postFix, ""));
                history.put(extractSpecialKey(file.getName(), postFix), tmp);
            });
        }
    }

    /**
     * Extracts the special key from a file name.<br><br>
     *
     * Example: "TradeStatistics2Store_1.3.4_BTC_MAINNET" becomes "1.3.4"
     *
     * @param name the file name to begin with
     * @param postFix the postfix eg. "BTC_MAINNET"
     * @return the special key eg. "1.3.4"
     */
    private String extractSpecialKey(String name, String postFix) {
        return name.replace(postFix, "") // remove the postfix
                .replace("_", "") // remove the spacings
                .replace(getFileName(), ""); // remove the file name
    }

    /**
     * Bluntly copy and pasted from {@link StoreService} because:
     * <ul><li>The member function there is private</li>
     * <li>it does not match our interface</li>
     * <li>is a temporary solutions, until https://github.com/bisq-network/projects/issues/29</li></ul>
     *
     * @param name
     * @return store
     */
    private T readStore(String name) {
        T store = storage.initAndGetPersistedWithFileName(name, 100);
        if (store != null) {
            log.info("{}: size of {}: {} MB", this.getClass().getSimpleName(),
                    storage.getClass().getSimpleName(),
                    store.toProtoMessage().toByteArray().length / 1_000_000D);
        } else {
            store = createStore();
        }

        return store;
    }

    /**
     * We compile a list of files available in resources and:<br>
     * <ul><li>copy them to our working directory</li>
     * <li>load the freshly copied stores</li>
     * <li>remove the contents from our live data store</li>
     * <li>persist the cleaned-up live store</li></ul>
     *
     * @param postFix
     */
    @Override
    protected void makeFileFromResourceFile(String postFix) {
        File dbDir = new File(absolutePathOfStorageDir);
        if (!dbDir.exists() && !dbDir.mkdir())
            log.warn("make dir failed.\ndbDir=" + dbDir.getAbsolutePath());

        // check resources for files
        List<String> versions = new ArrayList<>();
        versions.add(Version.VERSION);
        versions.addAll(Version.history);
        List<String> resourceFiles = versions.stream()
                .map(s -> getFileName() + "_" + s + postFix)
                .collect(Collectors.toList());

        // if not, copy and split
        resourceFiles.forEach(resourceFileName -> {
            final File destinationFile = new File(absolutePathOfStorageDir, resourceFileName.replace(postFix, ""));
            if (!destinationFile.exists()) {
                try {
                    log.info("We copy resource to file: resourceFileName={}, destinationFile={}", resourceFileName, destinationFile);
                    FileUtil.resourceToFile(resourceFileName, destinationFile);
                } catch (ResourceNotFoundException e) {
                    log.info("Could not find resourceFile {}. That is expected if none is provided yet.", resourceFileName);
                } catch (Throwable e) {
                    log.error("Could not copy resourceFile {} to {}.\n{}", resourceFileName, destinationFile.getAbsolutePath(), e.getMessage());
                    e.printStackTrace();
                }
            } else {
                log.debug(resourceFileName + " file exists already.");
            }
        });

        // split
        // - get all
        history = new HashMap<>();
        store = readStore(getFileName());
        resourceFiles.forEach(file -> {
            SplitStore tmp = readStore(file.replace(postFix, ""));
            history.put(extractSpecialKey(file, postFix), tmp);
            // - subtract all that is in resource files
            store.getMap().keySet().removeAll(tmp.getMap().keySet());
        });

        // - create new file with leftovers
        storage.initAndGetPersisted(store, 0);
        storage.queueUpForSave();

    }
}

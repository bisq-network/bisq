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

package bisq.core.account.witness;

import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.SplitStore;
import bisq.network.p2p.storage.persistence.SplitStoreService;

import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.storage.FileUtil;
import bisq.common.storage.ResourceNotFoundException;
import bisq.common.storage.Storage;

import javax.inject.Named;
import javax.inject.Inject;

import java.nio.file.Paths;

import java.io.File;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountAgeWitnessStorageService extends SplitStoreService<AccountAgeWitnessStore> {
    private static final String FILE_NAME = "AccountAgeWitnessStore";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountAgeWitnessStorageService(@Named(Config.STORAGE_DIR) File storageDir,
                                           Storage<AccountAgeWitnessStore> persistableNetworkPayloadMapStorage) {
        super(storageDir, persistableNetworkPayloadMapStorage);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    public boolean canHandle(PersistableNetworkPayload payload) {
        return payload instanceof AccountAgeWitness;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected void readFromResources(String postFix) {
        // check Version.VERSION and see if we have latest r/o data store file in working directory
        if (!new File(absolutePathOfStorageDir + File.separator + getFileName() + "_" + Version.VERSION).exists())
            makeFileFromResourceFile(postFix); // if we have the latest file, we are good, else do stuff // TODO are we?
        else {
            // load stores/storage
            File dbDir = new File(absolutePathOfStorageDir);
            List<File> resourceFiles = Arrays.asList(dbDir.list((dir, name) -> name.startsWith(getFileName() + "_"))).stream().map(s -> new File(s)).collect(Collectors.toList());

            history = new HashMap<>();
            store = readStore(getFileName());
            resourceFiles.forEach(file -> {
                SplitStore tmp = readStore(file.getName().replace(postFix, ""));
                history.put(file.getName().replace(postFix, "").replace(getFileName(), "").replace("_", ""), tmp);
            });
        }
    }

    @Override
    protected AccountAgeWitnessStore createStore() {
        return new AccountAgeWitnessStore();
    }

    private AccountAgeWitnessStore readStore(String name) {
        AccountAgeWitnessStore store = storage.initAndGetPersistedWithFileName(name, 100);
        if (store != null) {
            log.info("{}: size of {}: {} MB", this.getClass().getSimpleName(),
                    storage.getClass().getSimpleName(),
                    store.toProtoMessage().toByteArray().length / 1_000_000D);
        } else {
            store = createStore();
        }

        return store;
    }

    @Override
    protected void makeFileFromResourceFile(String postFix) {
        File dbDir = new File(absolutePathOfStorageDir);
        if (!dbDir.exists() && !dbDir.mkdir())
            log.warn("make dir failed.\ndbDir=" + dbDir.getAbsolutePath());

        // check resources for files
        File resourceDir = new File(ClassLoader.getSystemClassLoader().getResource("").getFile());
        List<File> resourceFiles = Arrays.asList(resourceDir.list((dir, name) -> name.startsWith(getFileName()))).stream().map(s -> new File(s)).collect(Collectors.toList());

        // if not, copy and split
        resourceFiles.forEach(file -> {
            final File destinationFile = new File(Paths.get(absolutePathOfStorageDir, file.getName().replace(postFix, "")).toString());
            final String resourceFileName = file.getName();
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
                log.debug(file.getName() + " file exists already.");
            }
        });

        // split
        // - get all
        history = new HashMap<>();
        store = readStore(getFileName());
        resourceFiles.forEach(file -> {
            SplitStore tmp = readStore(file.getName().replace(postFix, ""));
            history.put(file.getName().replace(postFix, "").replace(getFileName(), "").replace("_", ""), tmp);
            // - subtract all that is in resource files
            store.getMap().keySet().removeAll(tmp.getMap().keySet());
        });

        // - create new file with leftovers
        storage.queueUpForSave();

    }
}

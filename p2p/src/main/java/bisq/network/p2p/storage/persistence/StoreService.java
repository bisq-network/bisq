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

import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.storage.FileUtil;
import bisq.common.storage.ResourceNotFoundException;
import bisq.common.storage.Storage;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for handling of persisted data.
 * <p>
 * We handle several different cases:
 * <p>
 * 1   Check if local db file exists.
 * 1a  If it does not exist try to read the resource file.
 * 1aa If the resource file exists we copy it and use that as our local db file. We are done.
 * 1ab If the resource file does not exist we create a new fresh/empty db file. We are done.
 * 1b  If we have already a local db file we read it. We are done.
 */
@Slf4j
public abstract class StoreService<T extends PersistableEnvelope> {

    protected final Storage<T> storage;
    protected final String absolutePathOfStorageDir;

    protected T store;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public StoreService(File storageDir,
                        Storage<T> storage) {
        this.storage = storage;
        absolutePathOfStorageDir = storageDir.getAbsolutePath();

        storage.setNumMaxBackupFiles(1);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void persist() {
        storage.queueUpForSave(store, 200);
    }

    protected T getStore() {
        return store;
    }

    public abstract String getFileName();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void readFromResources(String postFix) {
        String fileName = getFileName();
        makeFileFromResourceFile(fileName, postFix);
        try {
            readStore();
        } catch (Throwable t) {
            try {
                storage.removeAndBackupFile(fileName);
            } catch (IOException e) {
                log.error(e.toString());
            }
            makeFileFromResourceFile(fileName, postFix);
            readStore();
        }
    }

    protected boolean makeFileFromResourceFile(String fileName, String postFix) {
        String resourceFileName = fileName + postFix;
        File dbDir = new File(absolutePathOfStorageDir);
        if (!dbDir.exists() && !dbDir.mkdir())
            log.warn("make dir failed.\ndbDir=" + dbDir.getAbsolutePath());

        File destinationFile = new File(Paths.get(absolutePathOfStorageDir, fileName).toString());
        if (!destinationFile.exists()) {
            try {
                log.info("We copy resource to file: resourceFileName={}, destinationFile={}", resourceFileName, destinationFile);
                FileUtil.resourceToFile(resourceFileName, destinationFile);
                return true;
            } catch (ResourceNotFoundException e) {
                log.info("Could not find resourceFile " + resourceFileName + ". That is expected if none is provided yet.");
            } catch (Throwable e) {
                log.error("Could not copy resourceFile " + resourceFileName + " to " +
                        destinationFile.getAbsolutePath() + ".\n" + e.getMessage());
                e.printStackTrace();
            }
        } else {
            log.info("No resource file have been copied. {} exists already.", fileName);
        }
        return false;
    }

    protected T getStore(String fileName) {
        T store = storage.initAndGetPersistedWithFileName(fileName, 100);
        if (store != null) {
            log.info("{}: size of {}: {} MB", this.getClass().getSimpleName(),
                    storage.getClass().getSimpleName(),
                    store.toProtoMessage().toByteArray().length / 1_000_000D);
        } else {
            store = createStore();
        }
        return store;
    }

    protected void readStore() {
        store = getStore(getFileName());
    }

    protected abstract T createStore();
}

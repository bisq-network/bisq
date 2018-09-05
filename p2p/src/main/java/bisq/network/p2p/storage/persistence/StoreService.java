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

import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.storage.FileUtil;
import bisq.common.storage.ResourceNotFoundException;
import bisq.common.storage.Storage;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class StoreService<T extends PersistableEnvelope, R extends PersistablePayload> {

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
        storage.queueUpForSave(store, 2000);
    }

    protected T getStore() {
        return store;
    }

    abstract public String getFileName();

    abstract public Map<P2PDataStorage.ByteArray, R> getMap();

    abstract public boolean canHandle(R payload);

    R putIfAbsent(P2PDataStorage.ByteArray hash, R payload) {
        R previous = getMap().putIfAbsent(hash, payload);
        persist();
        return previous;
    }

    R remove(P2PDataStorage.ByteArray hash) {
        final R result = getMap().remove(hash);
        persist();
        return result;
    }

    boolean containsKey(P2PDataStorage.ByteArray hash) {
        return getMap().containsKey(hash);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void readFromResources(String postFix) {
        makeFileFromResourceFile(postFix);
        try {
            readStore();
        } catch (Throwable t) {
            try {
                String fileName = getFileName();
                storage.removeAndBackupFile(fileName);
            } catch (IOException e) {
                log.error(e.toString());
            }
            makeFileFromResourceFile(postFix);
            readStore();
        }
    }

    protected void makeFileFromResourceFile(String postFix) {
        final String fileName = getFileName();
        String resourceFileName = fileName + postFix;
        File dbDir = new File(absolutePathOfStorageDir);
        if (!dbDir.exists() && !dbDir.mkdir())
            log.warn("make dir failed.\ndbDir=" + dbDir.getAbsolutePath());

        final File destinationFile = new File(Paths.get(absolutePathOfStorageDir, fileName).toString());
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
            log.debug(fileName + " file exists already.");
        }
    }


    protected void readStore() {
        final String fileName = getFileName();
        store = storage.initAndGetPersistedWithFileName(fileName, 100);
        if (store != null) {
            log.info("{}: size of {}: {} kb", this.getClass().getSimpleName(),
                    storage.getClass().getSimpleName(),
                    store.toProtoMessage().toByteArray().length / 100D);
        } else {
            store = createStore();
        }
    }

    abstract protected T createStore();
}

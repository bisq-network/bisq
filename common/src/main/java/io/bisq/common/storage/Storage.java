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

package io.bisq.common.storage;

import com.google.inject.Inject;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * That class handles the storage of a particular object to disk using Protobuffer.
 * <p/>
 * For every data object we write a separate file to minimize the risk of corrupted files in case of inconsistency from newer versions.
 * In case of a corrupted file we backup the old file to a separate directory, so if it holds critical data it might be helpful for recovery.
 * <p/>
 * We also backup at first read the file, so we have a valid file form the latest version in case a write operation corrupted the file.
 * <p/>
 * The read operation is triggered just at object creation (startup) and is at the moment not executed on a background thread to avoid asynchronous behaviour.
 * As the data are small and it is just one read access the performance penalty is small and might be even worse to create and setup a thread for it.
 * <p/>
 * The write operation used a background thread and supports a delayed write to avoid too many repeated write operations.
 */
public class Storage<T extends PersistableEnvelope> {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);
    public static final String STORAGE_DIR = "storageDir";

    private static DataBaseCorruptionHandler databaseCorruptionHandler;

    public static void setDatabaseCorruptionHandler(DataBaseCorruptionHandler databaseCorruptionHandler) {
        Storage.databaseCorruptionHandler = databaseCorruptionHandler;
    }

    public interface DataBaseCorruptionHandler {
        void onFileCorrupted(String fileName);
    }

    private final File dir;
    private FileManager<T> fileManager;
    private File storageFile;
    private T persistable;
    private String fileName;
    private int numMaxBackupFiles = 10;
    private final PersistenceProtoResolver persistenceProtoResolver;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Storage(@Named(STORAGE_DIR) File dir, PersistenceProtoResolver persistenceProtoResolver) {
        this.dir = dir;
        this.persistenceProtoResolver = persistenceProtoResolver;
    }

    @Nullable
    public T initAndGetPersistedWithFileName(String fileName, long delay) {
        this.fileName = fileName;
        storageFile = new File(dir, fileName);
        fileManager = new FileManager<>(dir, storageFile, delay, persistenceProtoResolver);
        return getPersisted();
    }

    @Nullable
    public T initAndGetPersisted(T persistable, long delay) {
        return initAndGetPersisted(persistable, persistable.getClass().getSimpleName(), delay);
    }

    @Nullable
    public T initAndGetPersisted(T persistable, String fileName, long delay) {
        this.persistable = persistable;
        this.fileName = fileName;
        storageFile = new File(dir, fileName);
        fileManager = new FileManager<>(dir, storageFile, delay, persistenceProtoResolver);
        return getPersisted();
    }

    public void queueUpForSave() {
        queueUpForSave(persistable);
    }

    public void queueUpForSave(long delayInMilli) {
        queueUpForSave(persistable, delayInMilli);
    }

    public void setNumMaxBackupFiles(int numMaxBackupFiles) {
        this.numMaxBackupFiles = numMaxBackupFiles;
    }

    // Save delayed and on a background thread
    public void queueUpForSave(T persistable) {
        if (persistable != null) {
            log.trace("save " + fileName);
            checkNotNull(storageFile, "storageFile = null. Call setupFileStorage before using read/write.");

            fileManager.saveLater(persistable);
        } else {
            log.trace("queueUpForSave called but no persistable set");
        }
    }

    public void queueUpForSave(T persistable, long delayInMilli) {
        if (persistable != null) {
            log.trace("save " + fileName);
            checkNotNull(storageFile, "storageFile = null. Call setupFileStorage before using read/write.");

            fileManager.saveLater(persistable, delayInMilli);
        } else {
            log.trace("queueUpForSave called but no persistable set");
        }
    }

    public void remove(String fileName) {
        fileManager.removeFile(fileName);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We do the file read on the UI thread to avoid problems from multi threading.
    // Data are small and read is done only at startup, so it is no performance issue.
    @Nullable
    private T getPersisted() {
        if (storageFile.exists()) {
            long now = System.currentTimeMillis();
            try {
                T persistedObject = fileManager.read(storageFile);
                log.trace("Read {} completed in {}msec", storageFile, System.currentTimeMillis() - now);

                // If we did not get any exception we can be sure the data are consistent so we make a backup
                now = System.currentTimeMillis();
                fileManager.backupFile(fileName, numMaxBackupFiles);
                log.trace("Backup {} completed in {}msec", storageFile, System.currentTimeMillis() - now);

                return persistedObject;
            } catch (Throwable t) {
                log.error("We cannot read the persisted data. " +
                        "We make a backup and remove the inconsistent file. fileName=" + fileName);
                log.error(t.getMessage());
                try {
                    // We keep a backup which might be used for recovery
                    fileManager.removeAndBackupFile(fileName);
                } catch (IOException e1) {
                    e1.printStackTrace();
                    log.error(e1.getMessage());
                    // We swallow Exception if backup fails
                }
                if (databaseCorruptionHandler != null)
                    databaseCorruptionHandler.onFileCorrupted(storageFile.getName());
            }
        }
        return null;
    }
}

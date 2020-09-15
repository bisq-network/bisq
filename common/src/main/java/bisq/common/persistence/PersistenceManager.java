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

package bisq.common.persistence;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import com.google.inject.Inject;

import javax.inject.Named;

import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.common.util.Preconditions.checkDir;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class PersistenceManager<T extends PersistableEnvelope> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final Map<String, PersistenceManager<?>> ALL_PERSISTENCE_MANAGER_MANAGERS = new HashMap<>();

    public static void flushAllDataToDisk(ResultHandler resultHandler) {
        ALL_PERSISTENCE_MANAGER_MANAGERS.values().forEach(persistenceManager -> {
            persistenceManager.flushAndShutDown();
            persistenceManager.close();
        });
        resultHandler.handleResult();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Priority {
        LOW(1),
        MID(5),
        HIGH(10);

        @Getter
        private final int numMaxBackupFiles;

        Priority(int numMaxBackupFiles) {

            this.numMaxBackupFiles = numMaxBackupFiles;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final File dir;
    private final PersistenceProtoResolver persistenceProtoResolver;
    private final CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler;
    private File storageFile;
    private T persistable;
    private String fileName;
    private Priority priority = Priority.MID;
    private Path usedTempFilePath;
    private boolean persistRequested;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PersistenceManager(@Named(Config.STORAGE_DIR) File dir,
                              PersistenceProtoResolver persistenceProtoResolver,
                              CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler) {
        this.dir = checkDir(dir);
        this.persistenceProtoResolver = persistenceProtoResolver;
        this.corruptedDatabaseFilesHandler = corruptedDatabaseFilesHandler;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(T persistable) {
        this.initialize(persistable, persistable.getDefaultStorageFileName(), Priority.MID);
    }

    public void initialize(T persistable, Priority priority) {
        this.initialize(persistable, persistable.getDefaultStorageFileName(), priority);
    }

    public void initialize(T persistable, String fileName) {
        this.initialize(persistable, fileName, Priority.MID);
    }

    public void initialize(T persistable, String fileName, Priority priority) {
        this.persistable = persistable;
        this.fileName = fileName;
        this.priority = priority;
        storageFile = new File(dir, fileName);
        ALL_PERSISTENCE_MANAGER_MANAGERS.put(fileName, this);
    }

    public void close() {
        ALL_PERSISTENCE_MANAGER_MANAGERS.remove(fileName);
    }

    private void flushAndShutDown() {
        if (persistRequested) {
            persistNow();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reading file
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public T getPersisted() {
        return getPersisted(checkNotNull(fileName));
    }

    @Nullable
    public T getPersisted(String fileName) {
        File storageFile = new File(dir, fileName);
        if (!storageFile.exists()) {
            return null;
        }

        long ts = System.currentTimeMillis();
        try (FileInputStream fileInputStream = new FileInputStream(storageFile)) {
            protobuf.PersistableEnvelope proto = protobuf.PersistableEnvelope.parseDelimitedFrom(fileInputStream);
            //noinspection unchecked
            T persistableEnvelope = (T) persistenceProtoResolver.fromProto(proto);
            log.info("Reading {} completed in {} ms", fileName, System.currentTimeMillis() - ts);
            return persistableEnvelope;
        } catch (Throwable t) {
            log.error("Reading {} failed with {}.", fileName, t.getMessage());
            try {
                // We keep a backup which might be used for recovery
                FileUtil.removeAndBackupFile(dir, storageFile, fileName, "backup_of_corrupted_data");
                DevEnv.logErrorAndThrowIfDevMode(t.toString());
            } catch (IOException e1) {
                e1.printStackTrace();
                log.error(e1.getMessage());
                // We swallow Exception if backup fails
            }
            if (corruptedDatabaseFilesHandler != null) {
                corruptedDatabaseFilesHandler.onFileCorrupted(storageFile.getName());
            }
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write file to disk
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void persistAtShutDown() {
        persistRequested = true;
    }

    public void persistNow() {
        long ts = System.currentTimeMillis();
        File tempFile = null;
        FileOutputStream fileOutputStream = null;

        try {
            // Before we write we backup existing file
            FileUtil.rollingBackup(dir, fileName, priority.getNumMaxBackupFiles());

            protobuf.PersistableEnvelope protoPersistable;
            try {
                protoPersistable = (protobuf.PersistableEnvelope) persistable.toPersistableMessage();
                if (protoPersistable.toByteArray().length == 0)
                    log.error("protoPersistable is empty. persistable=" + persistable.getClass().getSimpleName());
            } catch (Throwable e) {
                log.error("Error in saveToFile toProtoMessage: {}, {}", persistable.getClass().getSimpleName(), fileName);
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            if (!dir.exists() && !dir.mkdir())
                log.warn("make dir failed");

            tempFile = usedTempFilePath != null
                    ? FileUtil.createNewFile(usedTempFilePath)
                    : File.createTempFile("temp", null, dir);
            // Don't use a new temp file path each time, as that causes the delete-on-exit hook to leak memory:
            tempFile.deleteOnExit();

            fileOutputStream = new FileOutputStream(tempFile);

            protoPersistable.writeDelimitedTo(fileOutputStream);

            // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
            // to not write through to physical media for at least a few seconds, but this is the best we can do.
            fileOutputStream.flush();
            fileOutputStream.getFD().sync();

            // Close resources before replacing file with temp file because otherwise it causes problems on windows
            // when rename temp file
            fileOutputStream.close();

            FileUtil.renameFile(tempFile, storageFile);
            usedTempFilePath = tempFile.toPath();
        } catch (Throwable t) {
            // If an error occurred, don't attempt to reuse this path again, in case temp file cleanup fails.
            usedTempFilePath = null;
            log.error("Error at saveToFile, storageFile={}", fileName, t);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save. We will delete it now. storageFile={}", fileName);
                if (!tempFile.delete())
                    log.error("Cannot delete temp file.");
            }

            try {
                if (fileOutputStream != null)
                    fileOutputStream.close();
            } catch (IOException e) {
                // We swallow that
                e.printStackTrace();
                log.error("Cannot close resources." + e.getMessage());
            }
            log.error("Save {} completed in {} msec", fileName, System.currentTimeMillis() - ts);
            persistRequested = false;
        }
    }

    @Override
    public String toString() {
        return "PersistenceManager{" +
                "\n     dir=" + dir +
                ",\n     storageFile=" + storageFile +
                ",\n     persistable=" + persistable +
                ",\n     fileName='" + fileName + '\'' +
                ",\n     priority=" + priority +
                ",\n     usedTempFilePath=" + usedTempFilePath +
                ",\n     persistRequested=" + persistRequested +
                "\n}";
    }
}

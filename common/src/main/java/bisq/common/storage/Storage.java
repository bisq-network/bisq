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

package bisq.common.storage;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import com.google.inject.Inject;

import javax.inject.Named;

import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.common.util.Preconditions.checkDir;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Storage<T extends PersistableEnvelope> {
    private final File dir;
    private final PersistenceProtoResolver persistenceProtoResolver;
    private final CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler;

    private File storageFile;
    private T persistable;
    private String fileName;
    private int numMaxBackupFiles = 10;
    private Path usedTempFilePath;
    private final long delay = 100;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Storage(@Named(Config.STORAGE_DIR) File dir,
                   PersistenceProtoResolver persistenceProtoResolver,
                   CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler) {
        this.dir = checkDir(dir);
        this.persistenceProtoResolver = persistenceProtoResolver;
        this.corruptedDatabaseFilesHandler = corruptedDatabaseFilesHandler;

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                UserThread.execute(Storage.this::shutDown), "FileManager.ShutDownHook")
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(T persistable) {
        this.initialize(persistable, persistable.getDefaultStorageFileName());
    }

    public void initialize(T persistable, String fileName) {
        this.persistable = persistable;
        this.fileName = fileName;
        storageFile = new File(dir, fileName);
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
        try (final FileInputStream fileInputStream = new FileInputStream(storageFile)) {
            protobuf.PersistableEnvelope proto = protobuf.PersistableEnvelope.parseDelimitedFrom(fileInputStream);
            T persistableEnvelope = (T) persistenceProtoResolver.fromProto(proto);
            log.error("Read {} completed in {} ms", fileName, System.currentTimeMillis() - ts);
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

    public void queueUpForSave() {
        checkNotNull(persistable, "queueUpForSave: persistable must not be null. this=" + this);
        checkNotNull(storageFile, "queueUpForSave: storageFile must not be null. persistable=" + persistable.getClass().getSimpleName());
        log.error("queueUpForSave for {}", fileName);
        // Atomically set the value of the next write. This allows batching of multiple writes of the same data
        // structure if there are multiple calls to saveLater within a given `delayInMillis`.
        log.error("pre");
        UserThread.runAfter(() -> {
            try {
                log.error("run");
                long now = System.currentTimeMillis();
                saveToFile(persistable);
                log.error("Save {} completed in {} msec", fileName, System.currentTimeMillis() - now);
            } catch (Throwable e) {
                log.error("Error during saveFileTask", e);
            }
        }, delay, TimeUnit.MILLISECONDS);

        log.error("post");
    }

    //todo
    public void setNumMaxBackupFiles(int numMaxBackupFiles) {
        this.numMaxBackupFiles = numMaxBackupFiles;
    }

    public void removeAndBackupFile(String fileName) throws IOException {
        FileUtil.removeAndBackupFile(dir, storageFile, fileName, "backup_of_corrupted_data");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void saveToFile(T persistable) {
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        PrintWriter printWriter = null;

        try {
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
                //noinspection ConstantConditions,ConstantConditions
                if (printWriter != null)
                    printWriter.close();
            } catch (IOException e) {
                // We swallow that
                e.printStackTrace();
                log.error("Cannot close resources." + e.getMessage());
            }
        }
    }

    private void shutDown() {
        //todo
    }


    @Override
    public String toString() {
        return "Storage{" +
                "\n     dir=" + dir +
                ",\n     storageFile=" + storageFile +
                ",\n     persistable=" + persistable +
                ",\n     fileName='" + fileName + '\'' +
                ",\n     numMaxBackupFiles=" + numMaxBackupFiles +
                ",\n     usedTempFilePath=" + usedTempFilePath +
                ",\n     delay=" + delay +
                "\n}";
    }
}

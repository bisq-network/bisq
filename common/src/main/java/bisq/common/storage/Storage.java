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
import bisq.common.util.Utilities;

import com.google.inject.Inject;

import javax.inject.Named;

import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicReference<T> nextWrite;
    private final Callable<Void> saveFileTask;
    private final ScheduledThreadPoolExecutor executor;
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

        this.nextWrite = new AtomicReference<>(null);

        executor = Utilities.getScheduledThreadPoolExecutor("FileManager", 1, 10, 5);

        saveFileTask = () -> {
            try {
                Thread.currentThread().setName("Save-file-task-" + new Random().nextInt(10000));

                // Atomically take the next object to write and set the value to null so concurrent saveFileTask
                // won't duplicate work.
                T persistable = this.nextWrite.getAndSet(null);

                // If null, a concurrent saveFileTask already grabbed the data. Don't duplicate work.
                if (persistable == null)
                    return null;

                long now = System.currentTimeMillis();
                saveToFile(persistable, dir, storageFile);
                log.debug("Save {} completed in {} msec", storageFile, System.currentTimeMillis() - now);
            } catch (Throwable e) {
                log.error("Error during saveFileTask", e);
            }
            return null;
        };
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                UserThread.execute(Storage.this::shutDown), "FileManager.ShutDownHook")
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Queues up a save in the background. Useful for not very important wallet changes.
     */
    void saveLater(T persistable) {
        saveLater(persistable, delay);
    }

    public void saveLater(T persistable, long delayInMilli) {
        // Atomically set the value of the next write. This allows batching of multiple writes of the same data
        // structure if there are multiple calls to saveLater within a given `delayInMillis`.
        this.nextWrite.set(persistable);

        // Always schedule a write. It is possible that a previous saveLater was called with a larger `delayInMilli`
        // and we want the lower delay to execute. The saveFileTask handles concurrent operations.
        executor.schedule(saveFileTask, delayInMilli, TimeUnit.MILLISECONDS);
    }


    private synchronized void saveToFile(T persistable, File dir, File storageFile) {
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        PrintWriter printWriter = null;

        try {
            log.debug("Write to disc: {}", storageFile.getName());
            protobuf.PersistableEnvelope protoPersistable;
            try {
                protoPersistable = (protobuf.PersistableEnvelope) persistable.toPersistableMessage();
                if (protoPersistable.toByteArray().length == 0)
                    log.error("protoPersistable is empty. persistable=" + persistable.getClass().getSimpleName());
            } catch (Throwable e) {
                log.error("Error in saveToFile toProtoMessage: {}, {}", persistable.getClass().getSimpleName(), storageFile);
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

            log.debug("Writing protobuffer class:{} to file:{}", persistable.getClass(), storageFile.getName());
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
            log.error("Error at saveToFile, storageFile=" + storageFile.toString(), t);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save. We will delete it now. storageFile=" + storageFile);
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
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    @Nullable
    public T getPersisted() {
        if (!storageFile.exists()) {
            return null;
        }

        long ts = System.currentTimeMillis();
        try (final FileInputStream fileInputStream = new FileInputStream(storageFile)) {
            protobuf.PersistableEnvelope proto = protobuf.PersistableEnvelope.parseDelimitedFrom(fileInputStream);
            T persistableEnvelope = (T) persistenceProtoResolver.fromProto(proto);
            log.info("Read {} completed in {} ms", fileName, System.currentTimeMillis() - ts);
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

    public void removeAndBackupFile(String fileName) throws IOException {
        FileUtil.removeAndBackupFile(dir, storageFile, fileName, "backup_of_corrupted_data");
    }

    // TODO refactor old API


    @Nullable
    public T initAndGetPersistedWithFileName(String fileName, long delay) {
        this.fileName = fileName;
        storageFile = new File(dir, fileName);
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
            checkNotNull(storageFile, "storageFile = null. Call setupFileStorage before using read/write.");

            saveLater(persistable);
        } else {
            log.trace("queueUpForSave called but no persistable set");
        }
    }

    public void queueUpForSave(T persistable, long delayInMilli) {
        if (persistable != null) {
            checkNotNull(storageFile, "storageFile = null. Call setupFileStorage before using read/write.");

            saveLater(persistable, delayInMilli);
        } else {
            log.trace("queueUpForSave called but no persistable set");
        }
    }
}

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
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.common.util.concurrent.CycleDetectingLockFactory;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileManager<T extends PersistableEnvelope> {
    private final File dir;
    private final File storageFile;
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean savePending;
    private final long delay;
    private final Callable<Void> saveFileTask;
    private T persistable;
    private final PersistenceProtoResolver persistenceProtoResolver;
    private final ReentrantLock writeLock = CycleDetectingLockFactory.newInstance(CycleDetectingLockFactory.Policies.THROW).newReentrantLock("writeLock");

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FileManager(File dir, File storageFile, long delay, PersistenceProtoResolver persistenceProtoResolver) {
        this.dir = dir;
        this.storageFile = storageFile;
        this.persistenceProtoResolver = persistenceProtoResolver;

        executor = Utilities.getScheduledThreadPoolExecutor("FileManager", 1, 10, 5);

        // File must only be accessed from the auto-save executor from now on, to avoid simultaneous access.
        savePending = new AtomicBoolean();
        this.delay = delay;

        saveFileTask = () -> {
            try {
                Thread.currentThread().setName("Save-file-task-" + new Random().nextInt(10000));
                // Runs in an auto save thread.
                if (!savePending.getAndSet(false)) {
                    // Some other scheduled request already beat us to it.
                    return null;
                }
                saveNowInternal(persistable);
            } catch (Throwable e) {
                log.error("Error during saveFileTask", e);
            }
            return null;
        };
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            UserThread.execute(FileManager.this::shutDown);
        }, "FileManager.ShutDownHook"));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Actually write the wallet file to disk, using an atomic rename when possible. Runs on the current thread.
     */
    public void saveNow(T persistable) {
        saveNowInternal(persistable);
    }

    /**
     * Queues up a save in the background. Useful for not very important wallet changes.
     */
    public void saveLater(T persistable) {
        saveLater(persistable, delay);
    }

    public void saveLater(T persistable, long delayInMilli) {
        this.persistable = persistable;

        if (savePending.getAndSet(true))
            return;   // Already pending.

        executor.schedule(saveFileTask, delayInMilli, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    public synchronized T read(File file) {
        log.debug("Read from disc: {}", file.getName());

        try (final FileInputStream fileInputStream = new FileInputStream(file)) {
            PB.PersistableEnvelope persistable = PB.PersistableEnvelope.parseDelimitedFrom(fileInputStream);
            return (T) persistenceProtoResolver.fromProto(persistable);
        } catch (Throwable t) {
            String errorMsg = "Exception at proto read: " + t.getMessage() + " file:" + file.getAbsolutePath();
            log.error(errorMsg, t);
            //if(DevEnv.DEV_MODE)
            throw new RuntimeException(errorMsg);
        }
    }

    public synchronized void removeFile(String fileName) {
        log.debug("removeFile" + fileName);
        File file = new File(dir, fileName);
        boolean result = file.delete();
        if (!result)
            log.warn("Could not delete file: " + file.toString());

        File backupDir = new File(Paths.get(dir.getAbsolutePath(), "backup").toString());
        if (backupDir.exists()) {
            File backupFile = new File(Paths.get(dir.getAbsolutePath(), "backup", fileName).toString());
            if (backupFile.exists()) {
                result = backupFile.delete();
                if (!result)
                    log.warn("Could not delete backupFile: " + file.toString());
            }
        }
    }


    /**
     * Shut down auto-saving.
     */
    void shutDown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void removeAndBackupFile(String fileName) throws IOException {
        File corruptedBackupDir = new File(Paths.get(dir.getAbsolutePath(), "backup_of_corrupted_data").toString());
        if (!corruptedBackupDir.exists())
            if (!corruptedBackupDir.mkdir())
                log.warn("make dir failed");

        File corruptedFile = new File(Paths.get(dir.getAbsolutePath(), "backup_of_corrupted_data", fileName).toString());
        FileUtil.renameFile(storageFile, corruptedFile);
    }

    public synchronized void backupFile(String fileName, int numMaxBackupFiles) {
        FileUtil.rollingBackup(dir, fileName, numMaxBackupFiles);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void saveNowInternal(T persistable) {
        long now = System.currentTimeMillis();
        saveToFile(persistable, dir, storageFile);
        log.trace("Save {} completed in {} msec", storageFile, System.currentTimeMillis() - now);
    }

    private synchronized void saveToFile(T persistable, File dir, File storageFile) {
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        PrintWriter printWriter = null;

        try {
            log.debug("Write to disc: {}", storageFile.getName());
            PB.PersistableEnvelope protoPersistable;
            try {
                protoPersistable = (PB.PersistableEnvelope) persistable.toProtoMessage();
                if (protoPersistable.toByteArray().length == 0)
                    log.error("protoPersistable is empty. persistable=" + persistable.getClass().getSimpleName());
            } catch (Throwable e) {
                log.error("Error in saveToFile toProtoMessage: {}, {}", persistable.getClass().getSimpleName(), storageFile);
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            if (!dir.exists() && !dir.mkdir())
                log.warn("make dir failed");

            tempFile = File.createTempFile("temp", null, dir);
            tempFile.deleteOnExit();
            fileOutputStream = new FileOutputStream(tempFile);

            log.debug("Writing protobuffer class:{} to file:{}", persistable.getClass(), storageFile.getName());
            writeLock.lock();
            protoPersistable.writeDelimitedTo(fileOutputStream);

            // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
            // to not write through to physical media for at least a few seconds, but this is the best we can do.
            fileOutputStream.flush();
            fileOutputStream.getFD().sync();
            writeLock.unlock();

            // Close resources before replacing file with temp file because otherwise it causes problems on windows
            // when rename temp file
            fileOutputStream.close();
            FileUtil.renameFile(tempFile, storageFile);
        } catch (Throwable t) {
            log.error("Error at saveToFile, storageFile=" + storageFile.toString(), t);
        } finally {
            if (writeLock.isLocked())
                writeLock.unlock();
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
}

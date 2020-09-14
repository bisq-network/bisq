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

import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class FileManager<T extends PersistableEnvelope> {
    private final File dir;
    private final File storageFile;
    private final AtomicReference<T> nextWrite = new AtomicReference<>(null);
    private final PersistenceProtoResolver persistenceProtoResolver;
    private Path usedTempFilePath;
    private final Timer timer;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FileManager(File dir, File storageFile, long delay, PersistenceProtoResolver persistenceProtoResolver) {
        this.dir = dir;
        this.storageFile = storageFile;
        this.persistenceProtoResolver = persistenceProtoResolver;
        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Atomically take the next object to write and set the value to null
                T persistable = FileManager.this.nextWrite.getAndSet(null);
                if (persistable != null) {
                    saveToFile(persistable);
                }
            }
        }, delay, delay);

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                UserThread.execute(FileManager.this::shutDown), "FileManager.ShutDownHook")
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void saveLater(T persistable) {
        // Atomically set the value of the next write. This allows batching of multiple writes of the same data
        // structure if there are multiple calls to saveLater within a given `delayInMillis`.
        nextWrite.set(persistable);
    }

    // By default we use a new thread
    public void saveNow(T persistable, @Nullable Runnable completeHandler) {
        saveNow(persistable, Utilities.getSingleThreadExecutor("FileManager-saveNow-thread"), completeHandler);
    }

    public void saveNow(T persistable, Executor executor, @Nullable Runnable completeHandler) {
        nextWrite.set(persistable);

        executor.execute(() -> {
            T candidate = FileManager.this.nextWrite.getAndSet(null);
            if (candidate != null) {
                saveToFile(candidate);
            }

            if (completeHandler != null) {
                completeHandler.run();
            }
        });
    }

    @SuppressWarnings("unchecked")
    public synchronized T read(File file) {
        long ts = System.currentTimeMillis();
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            protobuf.PersistableEnvelope persistable = protobuf.PersistableEnvelope.parseDelimitedFrom(fileInputStream);
            T result = (T) persistenceProtoResolver.fromProto(persistable);
            log.info("Reading {} from disc took {} ms", file.getName(), System.currentTimeMillis() - ts);
            return result;
        } catch (Throwable t) {
            String errorMsg = "Exception at proto read: " + t.getMessage() + " file:" + file.getAbsolutePath();
            log.error(errorMsg, t);
            throw new RuntimeException(errorMsg, t);
        }
    }

    synchronized void removeFile(String fileName) {
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

    public void shutDown() {
        timer.cancel();
    }

    public static void removeAndBackupFile(File dbDir, File storageFile, String fileName, String backupFolderName)
            throws IOException {
        File corruptedBackupDir = new File(Paths.get(dbDir.getAbsolutePath(), backupFolderName).toString());
        if (!corruptedBackupDir.exists())
            if (!corruptedBackupDir.mkdir())
                log.warn("make dir failed");

        File corruptedFile = new File(Paths.get(dbDir.getAbsolutePath(), backupFolderName, fileName).toString());
        if (storageFile.exists()) {
            FileUtil.renameFile(storageFile, corruptedFile);
        }
    }

    synchronized void removeAndBackupFile(String fileName) throws IOException {
        removeAndBackupFile(dir, storageFile, fileName, "backup_of_corrupted_data");
    }

    synchronized void backupFile(String fileName, int numMaxBackupFiles) {
        FileUtil.rollingBackup(dir, fileName, numMaxBackupFiles);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private synchronized void saveToFile(T persistable) {
        long ts = System.currentTimeMillis();
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
            log.info("Write {} to disc took {} ms", storageFile.getName(), System.currentTimeMillis() - ts);
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

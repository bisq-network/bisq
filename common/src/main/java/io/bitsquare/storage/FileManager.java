/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.storage;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileManager<T> {
    private static final Logger log = LoggerFactory.getLogger(FileManager.class);

    private final File dir;
    private final File storageFile;
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean savePending;
    private final long delay;
    private final Callable<Void> saveFileTask;
    private T serializable;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FileManager(File dir, File storageFile, long delay) {
        this.dir = dir;
        this.storageFile = storageFile;

        executor = Utilities.getScheduledThreadPoolExecutor("FileManager", 1, 10, 5);

        // File must only be accessed from the auto-save executor from now on, to avoid simultaneous access.
        savePending = new AtomicBoolean();
        this.delay = delay;

        saveFileTask = () -> {
            Thread.currentThread().setName("Save-file-task-" + new Random().nextInt(10000));
            // Runs in an auto save thread.
            if (!savePending.getAndSet(false)) {
                // Some other scheduled request already beat us to it.
                return null;
            }
            saveNowInternal(serializable);
            return null;
        };

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    FileManager.this.shutDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Actually write the wallet file to disk, using an atomic rename when possible. Runs on the current thread.
     */
    public void saveNow(T serializable) {
        saveNowInternal(serializable);
    }

    /**
     * Queues up a save in the background. Useful for not very important wallet changes.
     */
    public void saveLater(T serializable) {
        saveLater(serializable, delay);
    }

    public void saveLater(T serializable, long delayInMilli) {
        this.serializable = serializable;

        if (savePending.getAndSet(true))
            return;   // Already pending.

        executor.schedule(saveFileTask, delayInMilli, TimeUnit.MILLISECONDS);
    }

    public synchronized T read(File file) throws IOException, ClassNotFoundException {
        log.debug("read" + file);
        try (final FileInputStream fileInputStream = new FileInputStream(file);
             final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            return (T) objectInputStream.readObject();
        } catch (Throwable t) {
            log.error("Exception at read: " + t.getMessage());
            throw t;
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
        renameTempFileToFile(storageFile, corruptedFile);
    }

    public synchronized void backupFile(String fileName) throws IOException {
        FileUtil.rollingBackup(dir, fileName);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void saveNowInternal(T serializable) {
        long now = System.currentTimeMillis();
        saveToFile(serializable, dir, storageFile);
        UserThread.execute(() -> log.trace("Save {} completed in {}msec", storageFile, System.currentTimeMillis() - now));
    }

    // TODO Sometimes we get a ConcurrentModificationException here
    private synchronized void saveToFile(T serializable, File dir, File storageFile) {
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            if (!dir.exists())
                if (!dir.mkdir())
                    log.warn("make dir failed");

            tempFile = File.createTempFile("temp", null, dir);

            // Don't use auto closeable resources in try() as we would need too many try/catch clauses (for tempFile)
            // and we need to close it
            // manually before replacing file with temp file
            fileOutputStream = new FileOutputStream(tempFile);
            objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(serializable);
            // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
            // to not write through to physical media for at least a few seconds, but this is the best we can do.
            fileOutputStream.flush();
            fileOutputStream.getFD().sync();

            // Close resources before replacing file with temp file because otherwise it causes problems on windows
            // when rename temp file
            fileOutputStream.close();
            objectOutputStream.close();

            renameTempFileToFile(tempFile, storageFile);
        } catch (Throwable t) {
            log.debug("storageFile " + storageFile.toString());
            t.printStackTrace();
            log.error("Error at saveToFile: " + t.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save. storageFile=" + storageFile);
                if (!tempFile.delete())
                    log.error("Cannot delete temp file.");
            }

            try {
                if (objectOutputStream != null)
                    objectOutputStream.close();
                if (fileOutputStream != null)
                    fileOutputStream.close();
            } catch (IOException e) {
                // We swallow that
                e.printStackTrace();
                log.error("Cannot close resources." + e.getMessage());
            }
        }
    }

    private synchronized void renameTempFileToFile(File tempFile, File file) throws IOException {
        if (Utils.isWindows()) {
            // Work around an issue on Windows whereby you can't rename over existing files.
            final File canonical = file.getCanonicalFile();
            if (canonical.exists() && !canonical.delete()) {
                throw new IOException("Failed to delete canonical file for replacement with save");
            }
            if (!tempFile.renameTo(canonical)) {
                throw new IOException("Failed to rename " + tempFile + " to " + canonical);
            }
        } else if (!tempFile.renameTo(file)) {
            throw new IOException("Failed to rename " + tempFile + " to " + file);
        }
    }
}

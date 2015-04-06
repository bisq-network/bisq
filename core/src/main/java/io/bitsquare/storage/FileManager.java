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

/**
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bitsquare.storage;


import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;

import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.nio.file.Paths;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Borrowed from BitcoinJ WalletFiles
 * A class that handles atomic and optionally delayed writing of a file to disk.
 * It can be useful to delay writing of a file to disk on slow devices.
 * By coalescing writes and doing serialization
 * and disk IO on a background thread performance can be improved.
 */
public class FileManager<T> {
    private static final Logger log = LoggerFactory.getLogger(FileManager.class);
    private static final ReentrantLock lock = Threading.lock("FileManager");
    private static Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

    private final File dir;
    private final File storageFile;
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean savePending;
    private final long delay;
    private final TimeUnit delayTimeUnit;
    private final Callable<Void> saver;
    private T serializable;

    public static void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        FileManager.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FileManager(File dir, File storageFile, long delay, TimeUnit delayTimeUnit) {
        this.dir = dir;
        this.storageFile = storageFile;

        ThreadFactoryBuilder builder = new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("FileManager thread")
                .setPriority(Thread.MIN_PRIORITY);  // Avoid competing with the GUI thread.

        builder.setUncaughtExceptionHandler(uncaughtExceptionHandler);

        // An executor that starts up threads when needed and shuts them down later.
        this.executor = new ScheduledThreadPoolExecutor(1, builder.build());
        this.executor.setKeepAliveTime(5, TimeUnit.SECONDS);
        this.executor.allowCoreThreadTimeOut(true);
        this.executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

        // File must only be accessed from the auto-save executor from now on, to avoid simultaneous access.
        this.savePending = new AtomicBoolean();
        this.delay = delay;
        this.delayTimeUnit = checkNotNull(delayTimeUnit);

        this.saver = () -> {
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
                    FileManager.this.shutdown();
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
    public void saveNow(T serializable) throws IOException {
        saveNowInternal(serializable);
    }

    /**
     * Queues up a save in the background. Useful for not very important wallet changes.
     */
    public void saveLater(T serializable) {
        this.serializable = serializable;

        if (savePending.getAndSet(true))
            return;   // Already pending.
        executor.schedule(saver, delay, delayTimeUnit);
    }

    public T read(File file) throws IOException, ClassNotFoundException {
        log.debug("read" + file);
        lock.lock();
        try (final FileInputStream fileInputStream = new FileInputStream(file);
             final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            return (T) objectInputStream.readObject();
        } finally {
            lock.unlock();
        }
    }

    public void removeFile(String fileName) {
        log.debug("removeFile" + fileName);
        File file = new File(dir, fileName);
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }


    /**
     * Shut down auto-saving.
     */
    public void shutdown() {
      /*  if (serializable != null)
            log.debug("shutDown " + serializable.getClass().getSimpleName());
        else
            log.debug("shutDown");*/

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS); // forever
        } catch (InterruptedException x) {
            throw new RuntimeException(x);
        }
    }

    public void removeAndBackupFile(String fileName) throws IOException {
        lock.lock();
        try {
            File corruptedBackupDir = new File(Paths.get(dir.getAbsolutePath(), "corrupted").toString());
            if (!corruptedBackupDir.exists())
                if (!corruptedBackupDir.mkdir())
                    log.warn("make dir failed");

            File corruptedFile = new File(Paths.get(dir.getAbsolutePath(), "corrupted", fileName).toString());
            renameTempFileToFile(storageFile, corruptedFile);
        } finally {
            lock.unlock();
        }
    }

    public void backupFile(String fileName) throws IOException {
        lock.lock();
        try {
            File backupDir = new File(Paths.get(dir.getAbsolutePath(), "backup").toString());
            if (!backupDir.exists())
                if (!backupDir.mkdir())
                    log.warn("make dir failed");

            File backupFile = new File(Paths.get(dir.getAbsolutePath(), "backup", fileName).toString());
            Files.copy(storageFile, backupFile);
        } finally {
            lock.unlock();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void saveNowInternal(T serializable) {
        long now = System.currentTimeMillis();
        saveToFile(serializable, dir, storageFile);
        Threading.USER_THREAD.execute(() -> log.info("Save {} completed in {}msec", storageFile, System.currentTimeMillis() - now));
    }

    private void saveToFile(T serializable, File dir, File storageFile) {
        lock.lock();
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

            // TODO ConcurrentModificationException happens sometimes at that line
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
            lock.unlock();
        }
    }

    private void renameTempFileToFile(File tempFile, File file) throws IOException {
        lock.lock();
        try {
            if (Utils.isWindows()) {
                // Work around an issue on Windows whereby you can't rename over existing files.
                final File canonical = file.getCanonicalFile();
                if (canonical.exists() && !canonical.delete()) {
                    throw new IOException("Failed to delete canonical file for replacement with save");
                }
                if (!tempFile.renameTo(canonical)) {
                    throw new IOException("Failed to rename " + tempFile + " to " + canonical);
                }
            }
            else if (!tempFile.renameTo(file)) {
                throw new IOException("Failed to rename " + tempFile + " to " + file);
            }
        } finally {
            lock.unlock();
        }
    }
}

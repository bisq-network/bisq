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

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.file.FileUtil;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.util.GcUtil;
import bisq.common.util.Utilities;

import com.google.inject.Inject;

import javax.inject.Named;

import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.common.util.Preconditions.checkDir;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Responsible for reading persisted data and writing it on disk. We read usually only at start-up and keep data in RAM.
 * We write all data which got a request for persistence at shut down at the very last moment when all other services
 * are shut down, so allowing changes to the data in the very last moment. For critical data we set {@link Source}
 * to HIGH which causes a timer to trigger a write to disk after 1 minute. We use that for not very frequently altered
 * data and data which cannot be recovered from the network.
 *
 * We decided to not use threading (as it was in previous versions) as the read operation happens only at start-up and
 * with the modified model that data is written at shut down we eliminate frequent and expensive disk I/O. Risks of
 * deadlock or data inconsistency and a more complex model have been a further argument for that model. In fact
 * previously we wasted a lot of resources as way too many threads have been created without doing actual work as well
 * the write operations got triggered way too often specially for the very frequent changes at SequenceNumberMap and
 * the very large DaoState (at dao blockchain sync that slowed down sync).
 *
 *
 * @param <T>   The type of the {@link PersistableEnvelope} to be written or read from disk
 */
@Slf4j
public class PersistenceManager<T extends PersistableEnvelope> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final Map<String, PersistenceManager<?>> ALL_PERSISTENCE_MANAGERS = new HashMap<>();
    private static boolean flushAtShutdownCalled;
    private static final AtomicBoolean allServicesInitialized = new AtomicBoolean(false);

    public static void onAllServicesInitialized() {
        allServicesInitialized.set(true);

        ALL_PERSISTENCE_MANAGERS.values().forEach(persistenceManager -> {
            // In case we got a requestPersistence call before we got initialized we trigger the timer for the
            // persist call
            if (persistenceManager.persistenceRequested) {
                persistenceManager.maybeStartTimerForPersistence();
            }
        });
    }

    public static void flushAllDataToDiskAtBackup(ResultHandler completeHandler) {
        flushAllDataToDisk(completeHandler, false);
    }

    public static void flushAllDataToDiskAtShutdown(ResultHandler completeHandler) {
        flushAllDataToDisk(completeHandler, true);
    }

    // We require being called only once from the global shutdown routine. As the shutdown routine has a timeout
    // and error condition where we call the method as well beside the standard path and it could be that those
    // alternative code paths call our method after it was called already, so it is a valid but rare case.
    // We add a guard to prevent repeated calls.
    private static void flushAllDataToDisk(ResultHandler completeHandler, boolean doShutdown) {
        if (!allServicesInitialized.get()) {
            log.warn("Application has not completed start up yet so we do not flush data to disk.");
            completeHandler.handleResult();
            return;
        }


        // We don't know from which thread we are called so we map to user thread
        UserThread.execute(() -> {
            if (doShutdown) {
                if (flushAtShutdownCalled) {
                    log.warn("We got flushAllDataToDisk called again. This can happen in some rare cases. We ignore the repeated call.");
                    return;
                }

                flushAtShutdownCalled = true;
            }

            log.info("Start flushAllDataToDisk");
            AtomicInteger openInstances = new AtomicInteger(ALL_PERSISTENCE_MANAGERS.size());

            if (openInstances.get() == 0) {
                log.info("No PersistenceManager instances have been created yet.");
                completeHandler.handleResult();
            }

            new HashSet<>(ALL_PERSISTENCE_MANAGERS.values()).forEach(persistenceManager -> {
                // For Priority.HIGH data we want to write to disk in any case to be on the safe side if we might have missed
                // a requestPersistence call after an important state update. Those are usually rather small data stores.
                // Otherwise we only persist if requestPersistence was called since the last persist call.
                // We also check if we have called read already to avoid a very early write attempt before we have ever
                // read the data, which would lead to a write of empty data
                // (fixes https://github.com/bisq-network/bisq/issues/4844).
                if (persistenceManager.readCalled.get() &&
                        (persistenceManager.source.flushAtShutDown || persistenceManager.persistenceRequested)) {
                    // We always get our completeHandler called even if exceptions happen. In case a file write fails
                    // we still call our shutdown and count down routine as the completeHandler is triggered in any case.

                    // We get our result handler called from the write thread so we map back to user thread.
                    persistenceManager.persistNow(() ->
                            UserThread.execute(() -> onWriteCompleted(completeHandler, openInstances, persistenceManager, doShutdown)));
                } else {
                    onWriteCompleted(completeHandler, openInstances, persistenceManager, doShutdown);
                }
            });
        });
    }

    // We get called always from user thread here.
    private static void onWriteCompleted(ResultHandler completeHandler,
                                         AtomicInteger openInstances,
                                         PersistenceManager<?> persistenceManager,
                                         boolean doShutdown) {
        if (doShutdown) {
            persistenceManager.shutdown();
        }

        if (openInstances.decrementAndGet() == 0) {
            log.info("flushAllDataToDisk completed");
            completeHandler.handleResult();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Source {
        // For data stores we received from the network and which could be rebuilt. We store only for avoiding too much network traffic.
        NETWORK(1, TimeUnit.MINUTES.toMillis(5), false),

        // For data stores which are created from private local data. This data could only be rebuilt from backup files.
        PRIVATE(10, 200, true),

        // For data stores which are created from private local data. Loss of that data would not have critical consequences.
        PRIVATE_LOW_PRIO(4, TimeUnit.MINUTES.toMillis(1), false);


        @Getter
        private final int numMaxBackupFiles;
        @Getter
        private final long delay;
        @Getter
        private final boolean flushAtShutDown;

        Source(int numMaxBackupFiles, long delay, boolean flushAtShutDown) {
            this.numMaxBackupFiles = numMaxBackupFiles;
            this.delay = delay;
            this.flushAtShutDown = flushAtShutDown;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final File dir;
    private final PersistenceProtoResolver persistenceProtoResolver;
    private final CorruptedStorageFileHandler corruptedStorageFileHandler;
    private File storageFile;
    private T persistable;
    private String fileName;
    private Source source = Source.PRIVATE_LOW_PRIO;
    private Path usedTempFilePath;
    private volatile boolean persistenceRequested;
    @Nullable
    private Timer timer;
    private ExecutorService writeToDiskExecutor;
    public final AtomicBoolean initCalled = new AtomicBoolean(false);
    public final AtomicBoolean readCalled = new AtomicBoolean(false);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PersistenceManager(@Named(Config.STORAGE_DIR) File dir,
                              PersistenceProtoResolver persistenceProtoResolver,
                              CorruptedStorageFileHandler corruptedStorageFileHandler) {
        this.dir = checkDir(dir);
        this.persistenceProtoResolver = persistenceProtoResolver;
        this.corruptedStorageFileHandler = corruptedStorageFileHandler;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(T persistable, Source source) {
        this.initialize(persistable, persistable.getDefaultStorageFileName(), source);
    }

    public void initialize(T persistable, String fileName, Source source) {
        if (flushAtShutdownCalled) {
            log.warn("We have started the shut down routine already. We ignore that initialize call.");
            return;
        }

        if (ALL_PERSISTENCE_MANAGERS.containsKey(fileName)) {
            RuntimeException runtimeException = new RuntimeException("We must not create multiple " +
                    "PersistenceManager instances for file " + fileName + ".");
            // We want to get logged from where we have been called so lets print the stack trace.
            runtimeException.printStackTrace();
            throw runtimeException;
        }

        if (initCalled.get()) {
            RuntimeException runtimeException = new RuntimeException("We must not call initialize multiple times. " +
                    "PersistenceManager for file: " + fileName + ".");
            // We want to get logged from where we have been called so lets print the stack trace.
            runtimeException.printStackTrace();
            throw runtimeException;
        }

        initCalled.set(true);

        this.persistable = persistable;
        this.fileName = fileName;
        this.source = source;
        storageFile = new File(dir, fileName);
        ALL_PERSISTENCE_MANAGERS.put(fileName, this);
    }

    public void shutdown() {
        ALL_PERSISTENCE_MANAGERS.remove(fileName);

        if (timer != null) {
            timer.stop();
        }

        if (writeToDiskExecutor != null) {
            writeToDiskExecutor.shutdown();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reading file
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Read persisted file in a thread.
     *
     * @param resultHandler     Consumer of persisted data once it was read from disk.
     * @param orElse            Called if no file exists or reading of file failed.
     */
    public void readPersisted(Consumer<T> resultHandler, Runnable orElse) {
        readPersisted(checkNotNull(fileName), resultHandler, orElse);
    }

    /**
     * Read persisted file in a thread.
     * We map result handler calls to UserThread, so clients don't need to worry about threading
     *
     * @param fileName          File name of our persisted data.
     * @param resultHandler     Consumer of persisted data once it was read from disk.
     * @param orElse            Called if no file exists or reading of file failed.
     */
    public void readPersisted(String fileName, Consumer<T> resultHandler, Runnable orElse) {
        if (flushAtShutdownCalled) {
            log.warn("We have started the shut down routine already. We ignore that readPersisted call.");
            return;
        }

        new Thread(() -> {
            T persisted = getPersisted(fileName);
            if (persisted != null) {
                UserThread.execute(() -> {
                    resultHandler.accept(persisted);

                    GcUtil.maybeReleaseMemory();
                });
            } else {
                UserThread.execute(orElse);
            }
        }, "PersistenceManager-read-" + fileName).start();
    }

    // API for synchronous reading of data. Not recommended to be used in application code.
    // Currently used by tests and monitor. Should be converted to the threaded API as well.
    @Nullable
    public T getPersisted() {
        return getPersisted(checkNotNull(fileName));
    }

    @Nullable
    public T getPersisted(String fileName) {
        if (flushAtShutdownCalled) {
            log.warn("We have started the shut down routine already. We ignore that getPersisted call.");
            return null;
        }

        readCalled.set(true);

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
            if (corruptedStorageFileHandler != null) {
                corruptedStorageFileHandler.addFile(storageFile.getName());
            }
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write file to disk
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestPersistence() {
        if (flushAtShutdownCalled) {
            log.warn("We have started the shut down routine already. We ignore that requestPersistence call.");
            return;
        }

        persistenceRequested = true;

        // If we have not initialized yet we postpone the start of the timer and call maybeStartTimerForPersistence at
        // onAllServicesInitialized
        if (!allServicesInitialized.get()) {
            return;
        }

        maybeStartTimerForPersistence();
    }

    private void maybeStartTimerForPersistence() {
        // We write to disk with a delay to avoid frequent write operations. Depending on the priority those delays
        // can be rather long.
        if (timer == null) {
            timer = UserThread.runAfter(() -> {
                persistNow(null);
                UserThread.execute(() -> timer = null);
            }, source.delay, TimeUnit.MILLISECONDS);
        }
    }

    public void forcePersistNow() {
        // Tor Bridges settings are edited before app init completes, require persistNow to be forced, see writeToDisk()
        persistNow(null, true);
    }

    public void persistNow(@Nullable Runnable completeHandler) {
        persistNow(completeHandler, false);
    }

    private void persistNow(@Nullable Runnable completeHandler, boolean force) {
        long ts = System.currentTimeMillis();
        try {
            // The serialisation is done on the user thread to avoid threading issue with potential mutations of the
            // persistable object. Keeping it on the user thread we are in a synchronize model.
            protobuf.PersistableEnvelope serialized = (protobuf.PersistableEnvelope) persistable.toPersistableMessage();

            // For the write to disk task we use a thread. We do not have any issues anymore if the persistable objects
            // gets mutated while the thread is running as we have serialized it already and do not operate on the
            // reference to the persistable object.
            getWriteToDiskExecutor().execute(() -> writeToDisk(serialized, completeHandler, force));

            long duration = System.currentTimeMillis() - ts;
            if (duration > 100) {
                log.info("Serializing {} took {} msec", fileName, duration);
            }
        } catch (Throwable e) {
            log.error("Error in saveToFile toProtoMessage: {}, {}", persistable.getClass().getSimpleName(), fileName);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void writeToDisk(protobuf.PersistableEnvelope serialized, @Nullable Runnable completeHandler, boolean force) {
        if (!allServicesInitialized.get() && !force) {
            log.warn("Application has not completed start up yet so we do not permit writing data to disk.");
            if (completeHandler != null)
                UserThread.execute(completeHandler);
            return;
        }

        long ts = System.currentTimeMillis();
        File tempFile = null;
        FileOutputStream fileOutputStream = null;

        try {
            // Before we write we backup existing file
            FileUtil.rollingBackup(dir, fileName, source.getNumMaxBackupFiles());

            if (!dir.exists() && !dir.mkdir())
                log.warn("make dir failed {}", fileName);

            tempFile = usedTempFilePath != null
                    ? FileUtil.createNewFile(usedTempFilePath)
                    : File.createTempFile("temp_" + fileName, null, dir);
            // Don't use a new temp file path each time, as that causes the delete-on-exit hook to leak memory:
            tempFile.deleteOnExit();

            fileOutputStream = new FileOutputStream(tempFile);

            serialized.writeDelimitedTo(fileOutputStream);

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
                if (!tempFile.delete()) {
                    log.error("Cannot delete temp file.");
                }
            }

            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                // We swallow that
                e.printStackTrace();
                log.error("Cannot close resources." + e.getMessage());
            }
            long duration = System.currentTimeMillis() - ts;
            if (duration > 100) {
                log.info("Writing the serialized {} completed in {} msec", fileName, duration);
            }
            persistenceRequested = false;
            if (completeHandler != null) {
                UserThread.execute(completeHandler);
            }
        }
    }

    private ExecutorService getWriteToDiskExecutor() {
        if (writeToDiskExecutor == null) {
            String name = "Write-" + fileName + "_to-disk";
            writeToDiskExecutor = Utilities.getSingleThreadExecutor(name);
        }
        return writeToDiskExecutor;
    }

    @Override
    public String toString() {
        return "PersistenceManager{" +
                "\n     fileName='" + fileName + '\'' +
                ",\n     dir=" + dir +
                ",\n     storageFile=" + storageFile +
                ",\n     persistable=" + persistable +
                ",\n     source=" + source +
                ",\n     usedTempFilePath=" + usedTempFilePath +
                ",\n     persistenceRequested=" + persistenceRequested +
                "\n}";
    }
}

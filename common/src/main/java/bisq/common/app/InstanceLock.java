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

package bisq.common.app;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.io.IOException;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Guards against running more than one instance of the application against the same
 * application data directory, which would otherwise lead to data and wallet corruption.
 *
 * <p>It acquires an OS-level advisory lock (via {@link FileChannel#tryLock()}) on a lock
 * file inside the data directory. The lock is held for the whole lifetime of the JVM and
 * is released automatically by the OS when the process exits — including on crash or kill,
 * which is why we deliberately do not rely on PID files alone.
 *
 * <p>Strong references to the {@link FileChannel} and {@link FileLock} are retained for the
 * instance lifetime: if they were garbage collected the lock would be released silently.
 */
@Slf4j
public class InstanceLock implements AutoCloseable {
    private static final String LOCK_FILE_NAME = "instance.lock";

    private final Path lockFilePath;

    // Held for the whole JVM lifetime. Must not be GC'd or the lock is released.
    private FileChannel channel;
    private FileLock fileLock;

    public InstanceLock(Path appDataDir) {
        this.lockFilePath = appDataDir.resolve(LOCK_FILE_NAME);
    }

    /**
     * Tries to acquire the exclusive single-instance lock.
     *
     * @return {@code true} if this process now holds the lock (it is the only instance),
     *         {@code false} if another running instance already holds it.
     * @throws IOException if the lock file or its directory cannot be created or accessed,
     *                     i.e. the locking mechanism itself is unusable.
     */
    public synchronized boolean tryLock() throws IOException {
        if (fileLock != null) {
            return true;
        }
        Files.createDirectories(lockFilePath.getParent());
        channel = FileChannel.open(lockFilePath,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            // tryLock returns null when the region is already locked by another process.
            fileLock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            // Region already locked by this same JVM. Treat as "another instance" defensively.
            fileLock = null;
        } catch (IOException e) {
            // The lock mechanism itself failed; close the channel before propagating so we
            // don't leak the file descriptor on the fail-open path.
            closeChannel();
            throw e;
        }

        if (fileLock == null) {
            closeChannel();
            return false;
        }

        writeOwnerMetadata();
        return true;
    }

    private void writeOwnerMetadata() {
        try {
            String info = ProcessHandle.current().pid() + System.lineSeparator();
            channel.truncate(0);
            ByteBuffer buffer = ByteBuffer.wrap(info.getBytes(StandardCharsets.UTF_8));
            channel.position(0);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            // No fsync (channel.force): the PID is a diagnostic hint, not durable state worth
            // surviving a crash. write() already makes it visible to other processes via the
            // page cache, which is all readOwnerPid needs.
        } catch (IOException e) {
            // Metadata is purely informational; failure does not affect the lock itself.
            log.warn("Could not write instance lock owner metadata to {}", lockFilePath, e);
        }
    }

    /**
     * @return the PID recorded by the instance currently holding the lock, if readable.
     *         Best-effort, for diagnostics only.
     */
    public Optional<Long> readOwnerPid() {
        try {
            String content = Files.readString(lockFilePath, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? Optional.empty() : Optional.of(Long.parseLong(content));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (fileLock != null && fileLock.isValid()) {
                fileLock.release();
            }
        } catch (IOException e) {
            log.warn("Failed to release instance lock {}", lockFilePath, e);
        } finally {
            fileLock = null;
            closeChannel();
        }
    }

    private void closeChannel() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            log.warn("Failed to close instance lock channel {}", lockFilePath, e);
        } finally {
            channel = null;
        }
    }
}

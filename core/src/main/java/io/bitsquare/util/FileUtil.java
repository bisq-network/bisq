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

package io.bitsquare.util;

import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;

import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil {
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);
    private static final ReentrantLock lock = Threading.lock("FileUtil");

    public static void write(Serializable serializable, File dir, File storageFile) throws IOException {
        lock.lock();
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            if (!dir.exists())
                dir.mkdir();

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

            writeTempFileToFile(tempFile, storageFile);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save.");
                if (!tempFile.delete()) log.error("Cannot delete temp file.");
            }

            try {
                if (objectOutputStream != null)
                    objectOutputStream.close();
                if (fileOutputStream != null)
                    fileOutputStream.close();
            } catch (IOException e) {
                // We swallow that
                e.printStackTrace();
                log.error("Cannot close resources.");
            }
            lock.unlock();
        }
    }

    public static Object read(File file) throws IOException, ClassNotFoundException {
        lock.lock();
        try (final FileInputStream fileInputStream = new FileInputStream(file);
             final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            return objectInputStream.readObject();
        } finally {
            lock.unlock();
        }
    }

    private static void writeTempFileToFile(File tempFile, File file) throws IOException {
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

    public static void removeAndBackupFile(File storageFile, File dir, String name) throws IOException {
        if (!dir.exists())
            dir.mkdir();

        writeTempFileToFile(storageFile, new File(dir, name));
    }

    public static void backupFile(File storageFile, File dir, String name) throws IOException {
        if (!dir.exists())
            dir.mkdir();

        Files.copy(storageFile, new File(dir, name));
    }
}

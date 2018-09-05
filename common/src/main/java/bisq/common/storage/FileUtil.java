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

import bisq.common.util.Utilities;

import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class FileUtil {
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    public static void rollingBackup(File dir, String fileName, int numMaxBackupFiles) {
        if (dir.exists()) {
            File backupDir = new File(Paths.get(dir.getAbsolutePath(), "backup").toString());
            if (!backupDir.exists())
                if (!backupDir.mkdir())
                    log.warn("make dir failed.\nBackupDir=" + backupDir.getAbsolutePath());

            File origFile = new File(Paths.get(dir.getAbsolutePath(), fileName).toString());
            if (origFile.exists()) {
                String dirName = "backups_" + fileName;
                if (dirName.contains("."))
                    dirName = dirName.replace(".", "_");
                File backupFileDir = new File(Paths.get(backupDir.getAbsolutePath(), dirName).toString());
                if (!backupFileDir.exists())
                    if (!backupFileDir.mkdir())
                        log.warn("make backupFileDir failed.\nBackupFileDir=" + backupFileDir.getAbsolutePath());

                File backupFile = new File(Paths.get(backupFileDir.getAbsolutePath(), new Date().getTime() + "_" + fileName).toString());

                try {
                    Files.copy(origFile, backupFile);

                    pruneBackup(backupFileDir, numMaxBackupFiles);
                } catch (IOException e) {
                    log.error("Backup key failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private static void pruneBackup(File backupDir, int numMaxBackupFiles) {
        if (backupDir.isDirectory()) {
            File[] files = backupDir.listFiles();
            if (files != null) {
                List<File> filesList = Arrays.asList(files);
                if (filesList.size() > numMaxBackupFiles) {
                    filesList.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
                    File file = filesList.get(0);
                    if (file.isFile()) {
                        if (!file.delete())
                            log.error("Failed to delete file: " + file);
                        else
                            pruneBackup(backupDir, numMaxBackupFiles);

                    } else {
                        pruneBackup(new File(Paths.get(backupDir.getAbsolutePath(), file.getName()).toString()), numMaxBackupFiles);
                    }
                }
            }
        }
    }

    public static void deleteDirectory(File file) throws IOException {
        deleteDirectory(file, null, true);
    }

    public static void deleteDirectory(File file, @Nullable File exclude, boolean ignoreLockedFiles) throws IOException {
        boolean excludeFileFound = false;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null)
                for (File f : files) {
                    if (!excludeFileFound)
                        excludeFileFound = f.equals(exclude);
                    if (!f.equals(exclude))
                        deleteDirectory(f, exclude, ignoreLockedFiles);
                }
        }
        // Finally delete main file/dir if exclude file was not found in directory
        if (!excludeFileFound && !file.equals(exclude)) {
            try {
                deleteFileIfExists(file, ignoreLockedFiles);
            } catch (Throwable t) {
                log.error("Could not delete file. Error=" + t.toString());
                throw new IOException(t);
            }
        }
    }

    public static void deleteFileIfExists(File file) throws IOException {
        deleteFileIfExists(file, true);
    }

    public static void deleteFileIfExists(File file, boolean ignoreLockedFiles) throws IOException {
        try {
            if (Utilities.isWindows())
                file = file.getCanonicalFile();

            if (file.exists() && !file.delete()) {
                if (ignoreLockedFiles) {
                    // We check if file is locked. On Windows all open files are locked by the OS, so we
                    if (isFileLocked(file))
                        log.info("Failed to delete locked file: " + file.getAbsolutePath());
                } else {
                    final String message = "Failed to delete file: " + file.getAbsolutePath();
                    log.error(message);
                    throw new IOException(message);
                }
            }
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            throw new IOException(t);
        }
    }

    private static boolean isFileLocked(File file) {
        return !file.canWrite();
    }

    public static void resourceToFile(String resourcePath, File destinationFile) throws ResourceNotFoundException, IOException {
        InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null)
            throw new ResourceNotFoundException(resourcePath);

        try (FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    public static void renameFile(File oldFile, File newFile) throws IOException {
        if (Utilities.isWindows()) {
            // Work around an issue on Windows whereby you can't rename over existing files.
            final File canonical = newFile.getCanonicalFile();
            if (canonical.exists() && !canonical.delete()) {
                throw new IOException("Failed to delete canonical file for replacement with save");
            }
            if (!oldFile.renameTo(canonical)) {
                throw new IOException("Failed to rename " + oldFile + " to " + canonical);
            }
        } else if (!oldFile.renameTo(newFile)) {
            throw new IOException("Failed to rename " + oldFile + " to " + newFile);
        }
    }

    public static void copyDirectory(File source, File destination) throws IOException {
        FileUtils.copyDirectory(source, destination);
    }
}

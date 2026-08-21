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

package bisq.common.file;

import bisq.common.util.Utilities;

import com.google.common.io.Files;

import org.apache.commons.io.IOUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class FileUtil {
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
                    filesList.sort(Comparator.comparing(File::getName));
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

    public static void deleteDirectory(File file,
                                       @Nullable File exclude,
                                       boolean ignoreLockedFiles) throws IOException {
        boolean excludeFileFound = false;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null)
                for (File f : files) {
                    boolean excludeFileFoundLocal = exclude != null && f.getAbsolutePath().equals(exclude.getAbsolutePath());
                    excludeFileFound |= excludeFileFoundLocal;
                    if (!excludeFileFoundLocal)
                        deleteDirectory(f, exclude, ignoreLockedFiles);
                }
        }
        // Finally delete main file/dir if exclude file was not found in directory
        if (!excludeFileFound && !(exclude != null && file.getAbsolutePath().equals(exclude.getAbsolutePath()))) {
            try {
                deleteFileIfExists(file, ignoreLockedFiles);
            } catch (Throwable t) {
                log.error("Could not delete file. Error=" + t);
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

    public static void resourceToFile(String resourcePath,
                                      File destinationFile) throws ResourceNotFoundException, IOException {
        try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new ResourceNotFoundException(resourcePath);
            }
            try (FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
                IOUtils.copy(inputStream, fileOutputStream);
            }
        }
    }

    public static List<String> listResourceDirectory(String directoryName) throws IOException, ResourceNotFoundException {
        URL url = Thread.currentThread().getContextClassLoader().getResource(directoryName);
        if (url == null) {
            throw new ResourceNotFoundException(directoryName);
        }
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        if (url.getProtocol().equals("file")) {
            File dir = new File(uri);
            String[] filenames = dir.list();
            if (filenames != null) {
                return List.of(filenames);
            }
        } else if (url.getProtocol().equals("jar")) {
            try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                 Stream<Path> filePaths = java.nio.file.Files.walk(fileSystem.getPath(directoryName), 1)) { //NOPMD
                return filePaths
                        .skip(1)
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toUnmodifiableList());
            }
        }
        throw new IOException("Failed to list resource directory: " + directoryName);
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

    public static void copyFile(File origin, File target) throws IOException {
        if (!origin.exists()) {
            return;
        }

        try {
            Files.copy(origin, target);
        } catch (IOException e) {
            log.error("Copy file failed", e);
            throw new IOException("Failed to copy " + origin + " to " + target);
        }

    }

    /**
     * Recursively copies {@code source} into {@code destination} using stream based file
     * copying (via {@link Files#copy}, backed by a {@link java.io.FileInputStream}).
     * <p>
     * This intentionally does not use {@code org.apache.commons.io.FileUtils.copyDirectory}.
     * Since commons-io 2.7 that delegates to NIO {@code java.nio.file.Files.copy}, which on
     * Windows uses the Win32 {@code CopyFileEx} API. {@code CopyFileEx} respects byte-range
     * locks, so copying a file that another process has locked a region of fails with
     * "The process cannot access the file because another process has locked a portion of
     * the file" and aborts the whole backup. bitcoinj holds such a lock on
     * {@code bisq.spvchain} while running. A plain {@code FileInputStream} read tolerates the
     * lock, restoring the behaviour Bisq relied on before the commons-io upgrade.
     */
    public static void copyDirectory(File source, File destination) throws IOException {
        // Match commons-io's copyDirectory input validation.
        if (!source.exists()) {
            throw new FileNotFoundException("Source '" + source.getAbsolutePath() + "' does not exist");
        }
        if (!source.isDirectory()) {
            throw new IllegalArgumentException("Source '" + source.getAbsolutePath() + "' exists but is not a directory");
        }
        // When the destination lies inside the source, exclude it from the walk so we don't
        // endlessly copy the destination into itself. Mirrors commons-io's copyDirectory,
        // which builds the same exclusion rather than rejecting such destinations.
        String excludedPath = null;
        String sourcePath = source.getCanonicalPath() + File.separator;
        String destinationPath = destination.getCanonicalPath() + File.separator;
        if (destinationPath.startsWith(sourcePath)) {
            excludedPath = destination.getCanonicalPath();
        }
        doCopyDirectory(source, destination, excludedPath);
    }

    private static void doCopyDirectory(File source, File destination, @Nullable String excludedPath) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Failed to create directory: " + destination.getAbsolutePath());
            }
            File[] files = source.listFiles();
            if (files == null) {
                // listFiles() returns null on an I/O error; treat it as a hard failure rather
                // than a silently empty directory, matching commons-io.
                throw new IOException("Failed to list contents of directory: " + source.getAbsolutePath());
            }
            for (File file : files) {
                if (excludedPath != null && file.getCanonicalPath().equals(excludedPath)) {
                    continue;
                }
                doCopyDirectory(file, new File(destination, file.getName()), excludedPath);
            }
            preserveLastModified(source, destination);
        } else {
            Files.copy(source, destination);
            preserveLastModified(source, destination);
        }
    }

    // Guava's Files.copy copies bytes only; restore the last-modified time (for both files and
    // directories) so the backup matches commons-io's preserveFileDate behaviour.
    private static void preserveLastModified(File source, File destination) {
        if (!destination.setLastModified(source.lastModified())) {
            log.warn("Could not preserve last-modified time of {}", destination.getAbsolutePath());
        }
    }

    public static File createNewFile(Path path) throws IOException {
        File file = path.toFile();
        if (!file.createNewFile()) {
            throw new IOException("There already exists a file with path: " + path);
        }
        return file;
    }

    public static void removeAndBackupFile(File dbDir, File storageFile, String fileName, String backupFolderName)
            throws IOException {
        File corruptedBackupDir = new File(Paths.get(dbDir.getAbsolutePath(), backupFolderName).toString());
        if (!corruptedBackupDir.exists() && !corruptedBackupDir.mkdir()) {
            log.warn("make dir failed");
        }

        File corruptedFile = new File(Paths.get(dbDir.getAbsolutePath(), backupFolderName, fileName).toString());
        if (storageFile.exists()) {
            renameFile(storageFile, corruptedFile);
        }
    }

    public static boolean doesFileContainKeyword(File file, String keyword) throws FileNotFoundException {
        try (Scanner s = new Scanner(file)) {
            while (s.hasNextLine()) {
                if (s.nextLine().contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }
}

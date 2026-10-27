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

import java.nio.channels.Channels;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
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
     * copying (reading with a {@link java.io.FileInputStream}).
     * <p>
     * This intentionally does not use {@code org.apache.commons.io.FileUtils.copyDirectory}.
     * Since commons-io 2.7 that delegates to NIO {@code java.nio.file.Files.copy}, which on
     * Windows uses the Win32 {@code CopyFileEx} API. {@code CopyFileEx} respects byte-range
     * locks, so copying a file that another process has locked a region of fails with
     * "The process cannot access the file because another process has locked a portion of
     * the file" and aborts the whole backup. bitcoinj holds such a lock on
     * {@code bisq.spvchain} while running. A plain {@code FileInputStream} read tolerates the
     * lock, restoring the behaviour Bisq relied on before the commons-io upgrade.
     * <p>
     * Symbolic links, junctions of Windows and special files such as named pipes inside {@code source} are neither
     * followed nor read: a link could make the copy read files outside {@code source}, and a named pipe would block it.
     * A {@code source} which is itself a link is followed.
     * <p>
     * The copy is a backup of the data directory with the keys of the user, so on a file system with POSIX
     * permissions the directories which it creates are accessible only by the owner (0700) and its files are readable
     * and writable only by the owner (0600), whatever the permissions in {@code source} and the umask are.
     *
     * @return the entries of {@code source} which are not copied, so that the caller can tell the user
     */
    public static List<File> copyDirectory(File source, File destination) throws IOException {
        // Match commons-io's copyDirectory input validation.
        if (!source.exists()) {
            throw new FileNotFoundException("Source '" + source.getAbsolutePath() + "' does not exist");
        }
        if (!source.isDirectory()) {
            throw new IllegalArgumentException("Source '" + source.getAbsolutePath() + "' exists but is not a directory");
        }
        Path sourceRoot = source.toPath().toRealPath();
        Path destinationRoot = destination.toPath();
        createOwnerOnlyDirectories(destinationRoot);
        // When the destination lies inside the source, exclude it from the walk so we don't
        // endlessly copy the destination into itself. Mirrors commons-io's copyDirectory,
        // which builds the same exclusion rather than rejecting such destinations. The walk
        // does not follow links, so it visits real paths below the real source.
        Path excludedPath = destinationRoot.toRealPath();
        List<File> notCopied = new ArrayList<>();
        java.nio.file.Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (directory.equals(excludedPath)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (attributes.isOther()) {
                    // A junction of Windows, which walkFileTree reports as a directory and would enter
                    notCopied.add(directory.toFile());
                    return FileVisitResult.SKIP_SUBTREE;
                }
                createOwnerOnlyDirectories(toDestination(directory));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) {
                    notCopied.add(file.toFile());
                    return FileVisitResult.CONTINUE;
                }
                Path destinationFile = toDestination(file);
                copyToOwnerOnlyFile(file.toFile(), destinationFile);
                preserveLastModified(file.toFile(), destinationFile.toFile());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException e) throws IOException {
                if (e != null) {
                    // A directory which cannot be read is a hard failure rather than a silently
                    // incomplete copy, matching commons-io.
                    throw e;
                }
                preserveLastModified(directory.toFile(), toDestination(directory).toFile());
                return FileVisitResult.CONTINUE;
            }

            private Path toDestination(Path path) {
                return destinationRoot.resolve(sourceRoot.relativize(path));
            }
        });
        notCopied.forEach(file -> log.warn("The copy of {} does not contain {}, which is a link or a special file",
                source, file));
        return notCopied;
    }

    // The permissions apply when a directory is created. A file system without them, for example FAT on a USB drive,
    // ignores them.
    private static void createOwnerOnlyDirectories(Path directory) throws IOException {
        java.nio.file.Files.createDirectories(directory, ownerOnly("rwx------"));
    }

    // Reads with a FileInputStream, see copyDirectory. The permissions apply when the file is created, so the content
    // is never readable by others.
    private static void copyToOwnerOnlyFile(File source, Path destination) throws IOException {
        Set<StandardOpenOption> options = Set.of(StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try (InputStream inputStream = new FileInputStream(source);
             OutputStream outputStream = Channels.newOutputStream(
                     java.nio.file.Files.newByteChannel(destination, options, ownerOnly("rw-------")))) {
            inputStream.transferTo(outputStream);
        }
    }

    // No attribute on a file system without POSIX permissions, such as the one of Windows
    private static FileAttribute<?>[] ownerOnly(String posixPermissions) {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[0];
        }
        return new FileAttribute<?>[]{
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(posixPermissions))};
    }

    // The copy copies bytes only; restore the last-modified time (for both files and
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

package io.bisq.api;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private Path appDataDirectoryPath;

    public BackupManager(String appDataDirectory) {
        this.appDataDirectoryPath = Paths.get(appDataDirectory);
    }

    public String createBackup() throws IOException {
        final Path backupDirectoryPath = getBackupDirectoryPath();
        if (Files.notExists(backupDirectoryPath))
            Files.createDirectory(backupDirectoryPath);

        final String dateString = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        final String backupFilename = "backup-" + dateString + ".zip";
        final Path backupFilePath = getBackupFilenameAndPath(backupFilename);

        final Path relativeBackupDirPath = appDataDirectoryPath.relativize(backupDirectoryPath);
        backup(appDataDirectoryPath, backupFilePath.toString(), path -> path.startsWith(relativeBackupDirPath));
        return backupFilename;
    }

    @NotNull
    private Path getBackupFilenameAndPath(String backupFilename) {
        return getBackupDirectoryPath().resolve(backupFilename);
    }

    @NotNull
    private Path getBackupDirectoryPath() {
        return appDataDirectoryPath.resolve("backup");
    }

    public List<String> getBackupList() {
        final File[] files = getBackupDirectoryPath().toFile().listFiles();
        if (null == files)
            return Collections.emptyList();
        return Arrays.asList(files).stream().map(File::getName).collect(Collectors.toList());
    }

    public FileInputStream getBackup(String fileName) throws FileNotFoundException {
        try {
            return new FileInputStream(getBackupFilenameAndPath(fileName).toFile());
        } catch (FileNotFoundException e) {
            throw fileNotFound(fileName);
        }
    }

    public boolean removeBackup(String fileName) throws FileNotFoundException {
        final File file = getBackupFilenameAndPath(fileName).toFile();
        if (!file.exists()) {
            throw fileNotFound(fileName);
        }
        return file.delete();
    }

    private void backup(Path sourceDir, String outputZipFilename, Function<Path, Boolean> shouldSkip) throws IOException {
        try (
                FileOutputStream out = new FileOutputStream(outputZipFilename);
                ZipOutputStream outputStream = new ZipOutputStream(out)
        ) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    try {
                        final Path targetFile = sourceDir.relativize(file);
                        if (shouldSkip.apply(targetFile))
                            return FileVisitResult.SKIP_SUBTREE;
                        outputStream.putNextEntry(new ZipEntry(targetFile.toString()));
                        final byte[] bytes = Files.readAllBytes(file);
                        outputStream.write(bytes, 0, bytes.length);
                        outputStream.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

    }

    @NotNull
    private FileNotFoundException fileNotFound(String fileName) {
        return new FileNotFoundException("File not found: " + fileName);
    }
}

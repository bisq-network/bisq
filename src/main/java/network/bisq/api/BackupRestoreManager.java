package network.bisq.api;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
public class BackupRestoreManager {

    private final Path appDirPath;
    private BackupManager backupManager;

    public BackupRestoreManager(Path appDirPath, BackupManager backupManager) {
        this.appDirPath = appDirPath;
        this.backupManager = backupManager;
    }

    public BackupRestoreManager(Path appDirPath) {
        this(appDirPath, new BackupManager(appDirPath));
    }

    public BackupRestoreManager(String appDir) {
        this(Paths.get(appDir));
    }

    public void requestRestore(String fileName) throws IOException {
        final Path backupFilePath = backupManager.getBackupFilePath(fileName);
        if (!backupFilePath.toFile().exists())
            throw new FileNotFoundException("File not found: " + fileName);
        Files.write(getRestoreBackupMarkerFilePath(), fileName.getBytes());
    }

    public void restoreIfRequested() throws IOException {
        final Path backupToRestoreMarkerPath = getRestoreBackupMarkerFilePath();
        final File backupToRestoreMarkerFile = backupToRestoreMarkerPath.toFile();
        if (!backupToRestoreMarkerFile.exists())
            return;
        final List<String> lines = Files.readAllLines(backupToRestoreMarkerPath);
        if (!backupToRestoreMarkerFile.delete())
            log.warn("Unable to remove backupToRestoreMarkerFile: " + backupToRestoreMarkerPath);
        final String backupFilename = lines.isEmpty() ? null : lines.get(0);
        if (null == backupFilename || backupFilename.trim().length() < 1)
            return;
        backupManager.restore(backupFilename);
        log.info("Backup restored successfully: " + backupFilename);
    }

    private Path getRestoreBackupMarkerFilePath() {
        return appDirPath.resolve("backup-to-restore");
    }
}

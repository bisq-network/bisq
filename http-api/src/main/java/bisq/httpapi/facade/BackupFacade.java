package bisq.httpapi.facade;

import bisq.core.app.BisqEnvironment;

import bisq.httpapi.BackupManager;
import bisq.httpapi.BackupRestoreManager;

import javax.inject.Inject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupFacade {

    private final ShutdownFacade shutdownFacade;
    private final BackupManager backupManager;
    private final BackupRestoreManager backupRestoreManager;

    @Inject
    public BackupFacade(BisqEnvironment bisqEnvironment, ShutdownFacade shutdownFacade) {
        this.shutdownFacade = shutdownFacade;

        String appDataDir = bisqEnvironment.getAppDataDir();
        backupManager = new BackupManager(appDataDir);
        backupRestoreManager = new BackupRestoreManager(appDataDir);
    }

    public String createBackup() throws IOException {
        return backupManager.createBackup();
    }

    public FileInputStream getBackup(String fileName) throws FileNotFoundException {
        return backupManager.getBackup(fileName);
    }

    public boolean removeBackup(String fileName) throws FileNotFoundException {
        return backupManager.removeBackup(fileName);
    }

    public List<String> getBackupList() {
        return backupManager.getBackupList();
    }

    public void requestBackupRestore(String fileName) throws IOException {
        backupRestoreManager.requestRestore(fileName);
        if (!shutdownFacade.isShutdownSupported()) {
            log.warn("No shutdown mechanism provided! You have to restart the app manually.");
            return;
        }
        log.info("Backup restore requested. Initiating shutdown.");
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            shutdownFacade.shutDown();
        }, "Shutdown before backup restore").start();
    }

    public void uploadBackup(String fileName, InputStream uploadedInputStream) throws IOException {
        backupManager.saveBackup(fileName, uploadedInputStream);
    }
}

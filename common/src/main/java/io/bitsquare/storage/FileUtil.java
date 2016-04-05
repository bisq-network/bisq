package io.bitsquare.storage;

import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class FileUtil {
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    public static void rollingBackup(File dir, String fileName) {
        File backupDir = new File(Paths.get(dir.getAbsolutePath(), "backup").toString());
        if (!backupDir.exists())
            if (!backupDir.mkdir())
                log.warn("make dir failed");

        File origFile = new File(Paths.get(dir.getAbsolutePath(), fileName).toString());
        if (origFile.exists()) {
            File backupFileDir = new File(Paths.get(backupDir.getAbsolutePath(), fileName.replace(".", "_")).toString());
            if (!backupFileDir.exists())
                if (!backupFileDir.mkdir())
                    log.warn("make backupFileDir failed");

            File backupFile = new File(Paths.get(backupFileDir.getAbsolutePath(), new Date().getTime() + "_" + fileName).toString());

            try {
                Files.copy(origFile, backupFile);

                pruneBackup(backupDir);
            } catch (IOException e) {
                log.error("Backup key failed " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void pruneBackup(File backupDir) {
        if (backupDir.isDirectory()) {
            File[] files = backupDir.listFiles();
            if (files != null) {
                List<File> filesList = Arrays.asList(files);
                if (filesList.size() > 10) {
                    filesList.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
                    File file = filesList.get(0);
                    if (file.isFile()) {
                        if (!file.delete())
                            log.error("Failed to delete file: " + file);
                    } else {
                        pruneBackup(new File(Paths.get(backupDir.getAbsolutePath(), file.getName()).toString()));
                    }
                }
            }
        }
    }

    public static void deleteDirectory(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null)
                for (File c : files)
                    deleteDirectory(c);
        }
        if (!file.delete())
            throw new FileNotFoundException("Failed to delete file: " + file);
    }
}

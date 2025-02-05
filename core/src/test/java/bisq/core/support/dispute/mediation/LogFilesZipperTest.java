package bisq.core.support.dispute.mediation;

import bisq.core.support.dispute.mediation.logs.LogFilesZipper;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LogFilesZipperTest {
    @Test
    void zipSingleFileTest(@TempDir Path dataDir) throws IOException {
        Path logFilePath = dataDir.resolve("bisq.log");
        String logFileContent = "Log file content";
        Files.writeString(logFilePath, logFileContent);

        List<Path> filesToZip = List.of(logFilePath);
        Path destinationPath = dataDir.resolve("logs.zip");
        new LogFilesZipper(destinationPath).zip(filesToZip);

        Path unzippedFilePath = dataDir.resolve("unzipped_file.txt");
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(destinationPath.toFile()))) {
            zipInputStream.getNextEntry();
            unzipFile(unzippedFilePath, zipInputStream);
        }

        String unzippedFileContent = Files.readString(unzippedFilePath);
        assertThat(unzippedFileContent, is(logFileContent));
    }

    @Test
    void zipMultipleFilesTest(@TempDir Path dataDir) throws IOException {
        Path logFilePath = dataDir.resolve("bisq.log");
        String logFileContent = "Log file content";
        Files.writeString(logFilePath, logFileContent);

        Path secondLogFilePath = dataDir.resolve("bisq_1.log");
        String secondLogFileContent = "Second log file content";
        Files.writeString(secondLogFilePath, secondLogFileContent);

        List<Path> filesToZip = List.of(logFilePath, secondLogFilePath);
        Path destinationPath = dataDir.resolve("logs.zip");
        new LogFilesZipper(destinationPath).zip(filesToZip);

        Path unzippedFilePath = dataDir.resolve("unzipped_file.txt");
        Path secondUnzippedFilePath = dataDir.resolve("second_unzipped_file.txt");
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(destinationPath.toFile()))) {
            zipInputStream.getNextEntry();
            unzipFile(unzippedFilePath, zipInputStream);

            zipInputStream.getNextEntry();
            unzipFile(secondUnzippedFilePath, zipInputStream);
        }

        String unzippedFileContent = Files.readString(unzippedFilePath);
        assertThat(unzippedFileContent, is(logFileContent));

        String secondUnzippedFileContent = Files.readString(secondUnzippedFilePath);
        assertThat(secondUnzippedFileContent, is(secondLogFileContent));
    }

    private void unzipFile(Path destinationPath, ZipInputStream zipInputStream) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(destinationPath.toFile())) {
            byte[] bytes = new byte[1024];
            int length;
            while ((length = zipInputStream.read(bytes)) > 0) {
                fileOutputStream.write(bytes, 0, length);
            }
        }
    }
}

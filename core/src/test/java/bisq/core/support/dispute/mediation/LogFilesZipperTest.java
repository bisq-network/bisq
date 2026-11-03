package bisq.core.support.dispute.mediation;

import bisq.core.support.dispute.mediation.logs.LogFilesZipper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LogFilesZipperTest {
    @Test
    void zipsTheFilesToRedactThroughTheRedaction(@TempDir Path dataDir) throws IOException {
        Path logFilePath = dataDir.resolve("bisq.log");
        Files.writeString(logFilePath, "secret of the node");
        Path redactedFilePath = dataDir.resolve("tor-stdout.log");
        // The byte 0xff is no valid UTF-8, and the redaction keeps it
        Files.write(redactedFilePath, new byte[]{'s', 'e', 'c', 'r', 'e', 't', ' ', (byte) 0xff});
        Path destinationPath = dataDir.resolve("logs.zip");

        new LogFilesZipper(destinationPath).zip(List.of(logFilePath.toFile(), redactedFilePath.toFile()),
                Map.of(redactedFilePath.toFile(), text -> text.replace("secret", "[redacted]")));

        Map<String, byte[]> entries = readEntries(destinationPath);
        assertThat(new String(entries.get("bisq.log"), StandardCharsets.UTF_8), is("secret of the node"));
        assertThat(entries.get("tor-stdout.log"),
                is(new byte[]{'[', 'r', 'e', 'd', 'a', 'c', 't', 'e', 'd', ']', ' ', (byte) 0xff}));
    }

    @Test
    void zipSingleFileTest(@TempDir Path dataDir) throws IOException {
        Path logFilePath = dataDir.resolve("bisq.log");
        String logFileContent = "Log file content";
        Files.writeString(logFilePath, logFileContent);

        List<File> filesToZip = List.of(logFilePath.toFile());
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

        List<File> filesToZip = List.of(logFilePath.toFile(), secondLogFilePath.toFile());
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

    private static Map<String, byte[]> readEntries(Path zipPath) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                entries.put(zipEntry.getName(), zipInputStream.readAllBytes());
            }
        }
        return entries;
    }
}

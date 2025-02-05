package bisq.core.support.dispute.mediation;

import bisq.core.support.dispute.mediation.logs.LogFilesFinder;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class LogFilesFinderTest {
    @TempDir Path dataDir;

    @Test
    void noLogFilesTest() {
        var logFilesFinder = new LogFilesFinder(dataDir);
        List<File> files = logFilesFinder.find();
        assertThat(files, is(empty()));
    }

    @Test
    void findSingleLogFileTest() throws IOException {
        Path logFilePath = dataDir.resolve("bisq.log");
        Files.writeString(logFilePath, "Log file content");

        var logFilesFinder = new LogFilesFinder(dataDir);
        List<File> files = logFilesFinder.find();

        assertThat(files, hasSize(1));
        assertThat(files, contains(logFilePath.toFile()));
    }

    @Test
    void findOnlySingleLogFileTest() throws IOException {
        Path bisqPropertiesPath = dataDir.resolve("bisq.properties");
        Files.writeString(bisqPropertiesPath, "Bisq properties");

        Path logFilePath = dataDir.resolve("bisq.log");
        Files.writeString(logFilePath, "Log file content");

        var logFilesFinder = new LogFilesFinder(dataDir);
        List<File> files = logFilesFinder.find();

        assertThat(files, hasSize(1));
        assertThat(files, contains(logFilePath.toFile()));
    }

    @Test
    void findMultipleLogFiles() throws IOException {
        var allLogFiles = new ArrayList<File>();

        Path logFilePath = dataDir.resolve("bisq.log");
        Files.writeString(logFilePath, "Log file content");
        allLogFiles.add(logFilePath.toFile());

        for (int i = 1; i < 5; i++) {
            logFilePath = dataDir.resolve("bisq_" + i + ".log");
            Files.writeString(logFilePath, "Log file content");
            allLogFiles.add(logFilePath.toFile());
        }

        var logFilesFinder = new LogFilesFinder(dataDir);
        List<File> files = logFilesFinder.find();

        assertThat(files, hasSize(allLogFiles.size()));
        assertThat(files, hasItems(allLogFiles.toArray(new File[0])));
    }

    @Test
    void findOnlyLogFiles() throws IOException {
        Path bisqPropertiesPath = dataDir.resolve("bisq.properties");
        Files.writeString(bisqPropertiesPath, "Bisq properties");

        var allLogFiles = new ArrayList<File>();

        Path logFilePath = dataDir.resolve("bisq.log");
        Files.writeString(logFilePath, "Log file content");
        allLogFiles.add(logFilePath.toFile());

        for (int i = 1; i < 5; i++) {
            logFilePath = dataDir.resolve("bisq_" + i + ".log");
            Files.writeString(logFilePath, "Log file content");
            allLogFiles.add(logFilePath.toFile());
        }

        var logFilesFinder = new LogFilesFinder(dataDir);
        List<File> files = logFilesFinder.find();

        assertThat(files, hasSize(allLogFiles.size()));
        assertThat(files, hasItems(allLogFiles.toArray(new File[0])));
    }
}

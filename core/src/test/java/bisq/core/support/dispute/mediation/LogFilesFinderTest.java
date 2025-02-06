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
    @TempDir
    Path dataDir;

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

    @Test
    void findNoLogFileWithKeyword() throws IOException {
        Path bisqPropertiesPath = dataDir.resolve("bisq.properties");
        Files.writeString(bisqPropertiesPath, "Bisq properties");

        Path logFilePath = dataDir.resolve("bisq.log");
        Files.writeString(logFilePath, "Log file content");

        for (int i = 1; i < 5; i++) {
            logFilePath = dataDir.resolve("bisq_" + i + ".log");
            Files.writeString(logFilePath, "Log file content");
        }

        var logFilesFinder = new LogFilesFinder(dataDir);
        List<File> files = logFilesFinder.findForTradeId("invalid_trade_id");

        assertThat(files, empty());
    }

    @Test
    void findSingleLogFileWithKeyword() throws IOException {
        Path bisqPropertiesPath = dataDir.resolve("bisq.properties");
        Files.writeString(bisqPropertiesPath, "Bisq properties");

        Path logFilePath = dataDir.resolve("bisq.log");
        Files.writeString(logFilePath, "Log file abcd content");

        var logFilesFinder = new LogFilesFinder(dataDir);
        List<File> files = logFilesFinder.findForTradeId("abcd");

        assertThat(files, hasSize(1));
        assertThat(files, hasItem(logFilePath.toFile()));
    }

    @Test
    void findLogFilesWithKeyword() throws IOException {
        Path bisqPropertiesPath = dataDir.resolve("bisq.properties");
        Files.writeString(bisqPropertiesPath, "Bisq properties");

        Path logFilePath = dataDir.resolve("bisq.log");
        Files.writeString(logFilePath, "Log file content");

        var logFilesContainingTradeId = new ArrayList<File>();

        for (int i = 1; i < 5; i++) {
            logFilePath = dataDir.resolve("bisq_" + i + ".log");

            String logFileContent;
            if (i == 1 || i == 3 || i == 4) {
                logFileContent = "Log file cont_abcd_ent";
                logFilesContainingTradeId.add(logFilePath.toFile());
            } else {
                logFileContent = "Log file content";
            }

            Files.writeString(logFilePath, logFileContent);
        }

        var logFilesFinder = new LogFilesFinder(dataDir);
        List<File> files = logFilesFinder.findForTradeId("abcd");

        assertThat(files, hasSize(logFilesContainingTradeId.size()));
        assertThat(files, hasItems(logFilesContainingTradeId.toArray(new File[0])));
    }
}

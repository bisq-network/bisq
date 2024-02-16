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

package bisq.persistence;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static bisq.persistence.DirectoryHasNChildren.hasNChildren;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RollingBackupsTests {
    private Path baseFilePath;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        baseFilePath = tempDir.resolve("file");
    }

    @Test
    void noBackup(@TempDir Path tempDir) {
        File file = new File(tempDir.toFile(), "file");
        assertThrows(IllegalArgumentException.class, () -> new RollingBackups(file, 0));
    }

    @Test
    void firstBackup(@TempDir Path tempDir) throws IOException {
        Files.writeString(baseFilePath, "ABC");
        assertThat(tempDir, hasNChildren(1));

        RollingBackups rollingBackups = new RollingBackups(baseFilePath.toFile(), 1);
        rollingBackups.rollBackups();

        assertThat(tempDir, hasNChildren(1));

        File backupFile = tempDir.resolve("file_0").toFile();
        String backupFileContent = Files.readString(backupFile.toPath());
        assertThat(backupFileContent, is("ABC"));
    }

    @Test
    void oneBackupWithExistingFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(baseFilePath, "NEW_CONTENT");

        Path backupPath = tempDir.resolve("file_0");
        Files.writeString(backupPath, "OLD_CONTENT");

        assertThat(tempDir, hasNChildren(2));

        RollingBackups rollingBackups = new RollingBackups(baseFilePath.toFile(), 1);
        rollingBackups.rollBackups();

        assertThat(tempDir, hasNChildren(1));
        String backupFileContent = Files.readString(backupPath);
        assertThat(backupFileContent, is("NEW_CONTENT"));
    }

    @Test
    void threeBackupsFirstBackup(@TempDir Path tempDir) throws IOException {
        Files.writeString(baseFilePath, "NEW_CONTENT");
        assertThat(tempDir, hasNChildren(1));

        RollingBackups rollingBackups = new RollingBackups(baseFilePath.toFile(), 3);
        rollingBackups.rollBackups();

        assertThat(tempDir, hasNChildren(1));

        Path backupPath = tempDir.resolve("file_0");
        String backupFileContent = Files.readString(backupPath);
        assertThat(backupFileContent, is("NEW_CONTENT"));
    }

    @Test
    void threeBackupsWithExistingFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(baseFilePath, "A");

        Path firstBackupPath = tempDir.resolve("file_0");
        Files.writeString(firstBackupPath, "B");

        Path secondBackupPath = tempDir.resolve("file_1");
        Files.writeString(secondBackupPath, "C");

        Path thirdBackupPath = tempDir.resolve("file_2");
        Files.writeString(thirdBackupPath, "D");

        assertThat(tempDir, hasNChildren(4));

        RollingBackups rollingBackups = new RollingBackups(baseFilePath.toFile(), 3);
        rollingBackups.rollBackups();

        assertThat(tempDir, hasNChildren(3));

        String firstBackupFileContent = Files.readString(firstBackupPath);
        assertThat(firstBackupFileContent, is("A"));

        String secondBackupFileContent = Files.readString(secondBackupPath);
        assertThat(secondBackupFileContent, is("B"));

        String thirdBackupFileContent = Files.readString(thirdBackupPath);
        assertThat(thirdBackupFileContent, is("C"));
    }

    @Test
    void threeBackupsFirstBackupMissing(@TempDir Path tempDir) throws IOException {
        Files.writeString(baseFilePath, "A");

        Path secondBackupPath = tempDir.resolve("file_1");
        Files.writeString(secondBackupPath, "C");

        Path thirdBackupPath = tempDir.resolve("file_2");
        Files.writeString(thirdBackupPath, "D");

        assertThat(tempDir, hasNChildren(3));

        RollingBackups rollingBackups = new RollingBackups(baseFilePath.toFile(), 3);
        rollingBackups.rollBackups();

        assertThat(tempDir, hasNChildren(2));

        Path firstBackupPath = tempDir.resolve("file_0");
        String firstBackupFileContent = Files.readString(firstBackupPath);
        assertThat(firstBackupFileContent, is("A"));

        String thirdBackupFileContent = Files.readString(thirdBackupPath);
        assertThat(thirdBackupFileContent, is("C"));
    }

    @Test
    void threeBackupsFileMissingInMiddles(@TempDir Path tempDir) throws IOException {
        Files.writeString(baseFilePath, "A");

        Path firstBackupPath = tempDir.resolve("file_0");
        Files.writeString(firstBackupPath, "B");

        Path thirdBackupPath = tempDir.resolve("file_2");
        Files.writeString(thirdBackupPath, "D");

        assertThat(tempDir, hasNChildren(3));

        RollingBackups rollingBackups = new RollingBackups(baseFilePath.toFile(), 3);
        rollingBackups.rollBackups();

        assertThat(tempDir, hasNChildren(3));

        String firstBackupFileContent = Files.readString(firstBackupPath);
        assertThat(firstBackupFileContent, is("A"));

        Path secondBackupPath = tempDir.resolve("file_1");
        String secondBackupFileContent = Files.readString(secondBackupPath);
        assertThat(secondBackupFileContent, is("B"));

        // Stays the same
        String thirdBackupFileContent = Files.readString(thirdBackupPath);
        assertThat(thirdBackupFileContent, is("D"));
    }
}

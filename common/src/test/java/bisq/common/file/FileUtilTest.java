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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class FileUtilTest {

    @Test
    public void copyDirectoryCopiesNestedFiles(@TempDir Path tmp) throws IOException {
        File source = tmp.resolve("source").toFile();
        File nested = new File(source, "btc_mainnet/wallet");
        assertTrue(nested.mkdirs());

        byte[] walletData = "wallet-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(new File(source, "bisq.properties").toPath(), "top".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(nested, "bisq.wallet").toPath(), walletData);

        File destination = tmp.resolve("backup").toFile();
        FileUtil.copyDirectory(source, destination);

        File copiedTop = new File(destination, "bisq.properties");
        File copiedWallet = new File(destination, "btc_mainnet/wallet/bisq.wallet");
        assertTrue(copiedTop.exists());
        assertTrue(copiedWallet.exists());
        assertArrayEquals(walletData, Files.readAllBytes(copiedWallet.toPath()));
    }

    @Test
    public void copyDirectoryWithMissingSourceThrows(@TempDir Path tmp) {
        File source = tmp.resolve("does-not-exist").toFile();
        File destination = tmp.resolve("backup").toFile();

        assertThrows(FileNotFoundException.class, () -> FileUtil.copyDirectory(source, destination));
        assertFalse(destination.exists());
    }

    @Test
    public void copyDirectoryWithNonDirectorySourceThrows(@TempDir Path tmp) throws IOException {
        File source = tmp.resolve("a-file").toFile();
        Files.write(source.toPath(), "data".getBytes(StandardCharsets.UTF_8));
        File destination = tmp.resolve("backup").toFile();

        assertThrows(IllegalArgumentException.class, () -> FileUtil.copyDirectory(source, destination));
        assertFalse(destination.exists());
    }

    @Test
    public void copyDirectoryPreservesLastModified(@TempDir Path tmp) throws IOException {
        File source = tmp.resolve("source").toFile();
        assertTrue(source.mkdirs());
        File file = new File(source, "bisq.wallet");
        Files.write(file.toPath(), "data".getBytes(StandardCharsets.UTF_8));
        long mtime = 1_600_000_000_000L; // fixed, well in the past
        assertTrue(file.setLastModified(mtime));

        File destination = tmp.resolve("backup").toFile();
        FileUtil.copyDirectory(source, destination);

        assertEquals(mtime, new File(destination, "bisq.wallet").lastModified());
    }

    @Test
    public void copyDirectoryPreservesNestedDirectoryLastModified(@TempDir Path tmp) throws IOException {
        File source = tmp.resolve("source").toFile();
        File nested = new File(source, "btc_mainnet");
        assertTrue(nested.mkdirs());
        Files.write(new File(nested, "bisq.wallet").toPath(), "data".getBytes(StandardCharsets.UTF_8));
        long mtime = 1_600_000_000_000L; // fixed, well in the past
        assertTrue(nested.setLastModified(mtime));

        File destination = tmp.resolve("backup").toFile();
        FileUtil.copyDirectory(source, destination);

        assertEquals(mtime, new File(destination, "btc_mainnet").lastModified());
    }

    @Test
    public void copyDirectoryWithDestinationInsideSourceExcludesDestination(@TempDir Path tmp) throws IOException {
        // Mirrors a user whose backup directory sits under the Bisq data directory: the copy
        // must succeed and must not recurse the destination into itself.
        File source = tmp.resolve("data").toFile();
        assertTrue(source.mkdirs());
        Files.write(new File(source, "bisq.wallet").toPath(), "data".getBytes(StandardCharsets.UTF_8));
        File destination = new File(source, "backup/bisq_backup");

        FileUtil.copyDirectory(source, destination);

        assertTrue(new File(destination, "bisq.wallet").exists());
        // Destination must not contain a copy of itself.
        assertFalse(new File(destination, "backup/bisq_backup").exists());
    }

    @Test
    public void copyDirectoryThrowsWhenDirectoryListingFails(@TempDir Path tmp) throws IOException {
        File source = tmp.resolve("data").toFile();
        File unreadable = new File(source, "locked");
        assertTrue(unreadable.mkdirs());
        Files.write(new File(source, "bisq.wallet").toPath(), "data".getBytes(StandardCharsets.UTF_8));

        // Only meaningful where the platform/user actually makes listFiles() return null
        // (e.g. Linux non-root); otherwise skip rather than assert a condition we can't create.
        assumeTrue(unreadable.setReadable(false, false));
        assumeTrue(unreadable.listFiles() == null);
        try {
            File destination = tmp.resolve("backup").toFile();
            assertThrows(IOException.class, () -> FileUtil.copyDirectory(source, destination));
        } finally {
            unreadable.setReadable(true, false);
        }
    }
}

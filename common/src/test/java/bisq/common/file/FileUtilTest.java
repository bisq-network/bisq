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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.time.Duration;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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
    public void copyDirectorySkipsTheGivenDirectories(@TempDir Path tmp) throws IOException {
        File source = tmp.resolve("source").toFile();
        File torDir = new File(source, "btc_mainnet/tor");
        File torDataDir = new File(torDir, "data");
        assertTrue(torDataDir.mkdirs());
        Files.write(new File(torDir, "hiddenservice").toPath(), "key".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(torDataDir, "state").toPath(), "state".getBytes(StandardCharsets.UTF_8));

        File destination = tmp.resolve("backup").toFile();
        FileUtil.copyDirectory(source, destination, Set.of(torDataDir));

        assertTrue(new File(destination, "btc_mainnet/tor/hiddenservice").exists());
        assertFalse(new File(destination, "btc_mainnet/tor/data").exists());
    }

    @Test
    public void copyDirectorySkipsTheGivenFiles(@TempDir Path tmp) throws IOException {
        File source = tmp.resolve("source").toFile();
        File torDir = new File(source, "btc_mainnet/tor");
        assertTrue(torDir.mkdirs());
        File dataModeRecord = new File(torDir, "data-mode");
        Files.write(dataModeRecord.toPath(), "unsandboxed".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(torDir, "torrc").toPath(), "torrc".getBytes(StandardCharsets.UTF_8));

        File destination = tmp.resolve("backup").toFile();
        List<File> notCopied = FileUtil.copyDirectory(source, destination, Set.of(dataModeRecord));

        assertTrue(new File(destination, "btc_mainnet/tor/torrc").exists());
        assertFalse(new File(destination, "btc_mainnet/tor/data-mode").exists());
        // A skipped entry is left out on purpose, so the user is not told about it
        assertTrue(notCopied.isEmpty());
    }

    @Test
    public void copyDirectoryCreatesACopyWhichOnlyTheOwnerCanAccess(@TempDir Path tmp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path source = Files.createDirectories(tmp.resolve("source"));
        Path hiddenServiceDir = Files.createDirectories(source.resolve("btc_mainnet/tor/hiddenservice"));
        Path privateKey = Files.write(hiddenServiceDir.resolve("private_key"), "key".getBytes(StandardCharsets.UTF_8));
        // Files of the data directory which others can read, as Bisq creates them with the default umask
        Files.setPosixFilePermissions(source.resolve("btc_mainnet"), PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString("rw-r--r--"));

        Path destination = tmp.resolve("backups").resolve("bisq_backup");
        FileUtil.copyDirectory(source.toFile(), destination.toFile());

        Set<PosixFilePermission> ownerOnlyDirectory = PosixFilePermissions.fromString("rwx------");
        assertEquals(ownerOnlyDirectory, Files.getPosixFilePermissions(destination));
        assertEquals(ownerOnlyDirectory, Files.getPosixFilePermissions(destination.resolve("btc_mainnet")));
        assertEquals(PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(destination.resolve("btc_mainnet/tor/hiddenservice/private_key")));
        assertEquals("key", Files.readString(destination.resolve("btc_mainnet/tor/hiddenservice/private_key")));
    }

    @Test
    public void copyDirectoryDoesNotFollowALinkAndReportsIt(@TempDir Path tmp) throws IOException {
        Path source = Files.createDirectories(tmp.resolve("source"));
        Files.write(source.resolve("bisq.properties"), "top".getBytes(StandardCharsets.UTF_8));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.write(outside.resolve("id_rsa"), "secret".getBytes(StandardCharsets.UTF_8));
        Path directoryLink = source.resolve("wallet");
        Path fileLink = source.resolve("key");
        createSymbolicLinkOrSkip(directoryLink, outside);
        createSymbolicLinkOrSkip(fileLink, outside.resolve("id_rsa"));

        File destination = tmp.resolve("backup").toFile();
        List<File> notCopied = FileUtil.copyDirectory(source.toFile(), destination);

        assertTrue(new File(destination, "bisq.properties").exists());
        assertFalse(Files.exists(destination.toPath().resolve("wallet"), LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(destination.toPath().resolve("key"), LinkOption.NOFOLLOW_LINKS));
        Path realSource = source.toRealPath();
        assertEquals(Set.of(realSource.resolve("wallet").toFile(), realSource.resolve("key").toFile()),
                Set.copyOf(notCopied));
        assertEquals("secret", Files.readString(outside.resolve("id_rsa")));
    }

    @Test
    public void copyDirectoryDoesNotReadANamedPipe(@TempDir Path tmp) throws Exception {
        Path source = Files.createDirectories(tmp.resolve("source"));
        Path namedPipe = source.resolve("pipe");
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        try {
            Process mkfifo = new ProcessBuilder("mkfifo", namedPipe.toString()).start();
            assumeTrue(mkfifo.waitFor() == 0);
        } catch (IOException e) {
            assumeTrue(false, "mkfifo is not available: " + e);
        }

        File destination = tmp.resolve("backup").toFile();
        // Reading the pipe would block until another process writes to it
        List<File> notCopied = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> FileUtil.copyDirectory(source.toFile(), destination));

        assertEquals(List.of(namedPipe.toRealPath().toFile()), notCopied);
        assertFalse(Files.exists(destination.toPath().resolve("pipe"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void copyDirectoryFollowsASourceWhichIsALinkAndStillSkipsTheGivenDirectories(@TempDir Path tmp)
            throws IOException {
        // For example a data directory which the user moved to another drive
        Path realSource = Files.createDirectories(tmp.resolve("drive").resolve("Bisq"));
        Path torDataDir = Files.createDirectories(realSource.resolve("btc_mainnet/tor/data"));
        Files.write(torDataDir.resolve("state"), "state".getBytes(StandardCharsets.UTF_8));
        Files.write(realSource.resolve("bisq.properties"), "top".getBytes(StandardCharsets.UTF_8));
        Path source = tmp.resolve("Bisq");
        createSymbolicLinkOrSkip(source, realSource);

        File destination = tmp.resolve("backup").toFile();
        List<File> notCopied = FileUtil.copyDirectory(source.toFile(), destination,
                Set.of(source.resolve("btc_mainnet/tor/data").toFile()));

        assertTrue(notCopied.isEmpty());
        assertTrue(new File(destination, "bisq.properties").exists());
        assertTrue(new File(destination, "btc_mainnet/tor").exists());
        assertFalse(new File(destination, "btc_mainnet/tor/data").exists());
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Symbolic links are not supported: " + e);
        }
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

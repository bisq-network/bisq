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

package bisq.common.setup;

import bisq.common.UserThread;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.IOException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CommonSetupTest {

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
    }

    // A data directory of an earlier version, created with the default umask
    @Test
    void appDataDirBecomesOwnerOnly(@TempDir Path tmp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path appDataDir = Files.createDirectories(tmp.resolve("Bisq"));
        Files.setPosixFilePermissions(appDataDir, PosixFilePermissions.fromString("rwxr-xr-x"));

        CommonSetup.restrictAppDataDirToOwner(appDataDir.toFile());

        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(appDataDir));
    }

    // Another user who opened a directory inside the data directory of an earlier version keeps opening the files
    // written there later, until the mode of that directory itself is changed
    @Test
    void everyDirectoryOfTheAppDataDirBecomesOwnerOnlyAndFilesKeepTheirModes(@TempDir Path tmp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path appDataDir = Files.createDirectories(tmp.resolve("Bisq"));
        Path btcDir = appDataDir.resolve("btc_mainnet");
        Path walletDir = btcDir.resolve("wallet");
        Path walletBackupDir = Files.createDirectories(walletDir.resolve("backup/backups_bisq_BTC_wallet"));
        Path dbDir = Files.createDirectories(btcDir.resolve("db"));
        Path wallet = Files.write(walletDir.resolve("bisq_BTC.wallet"), new byte[]{1});
        List<Path> directories = List.of(appDataDir, btcDir, walletDir, walletBackupDir.getParent(), walletBackupDir,
                dbDir);
        for (Path directory : directories) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        Files.setPosixFilePermissions(wallet, PosixFilePermissions.fromString("rw-r--r--"));

        CommonSetup.restrictAppDataDirToOwner(appDataDir.toFile());

        for (Path directory : directories) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory),
                    directory.toString());
        }
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"), Files.getPosixFilePermissions(wallet));
    }

    // For example a data directory which the user moved to another drive
    @Test
    void theDirectoriesOfAnAppDataDirWhichIsALinkBecomeOwnerOnly(@TempDir Path tmp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path realAppDataDir = Files.createDirectories(tmp.resolve("drive/Bisq"));
        Path walletDir = Files.createDirectories(realAppDataDir.resolve("btc_mainnet/wallet"));
        List<Path> directories = List.of(realAppDataDir, walletDir.getParent(), walletDir);
        for (Path directory : directories) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        Path appDataDir = tmp.resolve("Bisq");
        createSymbolicLinkOrSkip(appDataDir, realAppDataDir);

        CommonSetup.restrictAppDataDirToOwner(appDataDir.toFile());

        for (Path directory : directories) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory),
                    directory.toString());
        }
    }

    // For example a link to a wallet directory on another drive: the directories outside keep their modes
    @Test
    void aLinkInTheAppDataDirIsNotFollowed(@TempDir Path tmp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path appDataDir = Files.createDirectories(tmp.resolve("Bisq"));
        Path outside = Files.createDirectories(tmp.resolve("outside/inner"));
        List<Path> outsideDirectories = List.of(outside.getParent(), outside);
        for (Path directory : outsideDirectories) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        createSymbolicLinkOrSkip(appDataDir.resolve("wallet"), outside.getParent());

        CommonSetup.restrictAppDataDirToOwner(appDataDir.toFile());

        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(appDataDir));
        for (Path directory : outsideDirectories) {
            assertEquals(PosixFilePermissions.fromString("rwxr-xr-x"), Files.getPosixFilePermissions(directory),
                    directory.toString());
        }
    }

    // A directory which its owner cannot read, but others can enter, becomes owner-only with the directories below it
    @Test
    void aDirectoryWhichCannotBeReadBecomesOwnerOnlyWithItsSubtree(@TempDir Path tmp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path appDataDir = Files.createDirectories(tmp.resolve("Bisq"));
        Path inner = Files.createDirectories(appDataDir.resolve("a/inner"));
        Path unreadable = inner.getParent();
        Path readable = Files.createDirectories(appDataDir.resolve("b/c"));
        for (Path directory : List.of(inner, readable.getParent(), readable)) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("-wx--x--x"));
        try {
            // Only meaningful where the owner cannot list the directory, which is not the case for root
            assumeTrue(unreadable.toFile().listFiles() == null);

            CommonSetup.restrictAppDataDirToOwner(appDataDir.toFile());

            for (Path directory : List.of(appDataDir, unreadable, inner, readable.getParent(), readable)) {
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory),
                        directory.toString());
            }
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void anAppDataDirWhichCannotBeReadBecomesOwnerOnlyWithItsSubtree(@TempDir Path tmp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path appDataDir = Files.createDirectories(tmp.resolve("Bisq"));
        Path inner = Files.createDirectories(appDataDir.resolve("btc_mainnet"));
        Files.setPosixFilePermissions(inner, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.setPosixFilePermissions(appDataDir, PosixFilePermissions.fromString("-wx--x--x"));
        try {
            assumeTrue(appDataDir.toFile().listFiles() == null);

            CommonSetup.restrictAppDataDirToOwner(appDataDir.toFile());

            for (Path directory : List.of(appDataDir, inner)) {
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory),
                        directory.toString());
            }
        } finally {
            Files.setPosixFilePermissions(appDataDir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void aMissingAppDataDirIsOnlyLogged(@TempDir Path tmp) {
        assertDoesNotThrow(() -> CommonSetup.restrictAppDataDirToOwner(tmp.resolve("missing").toFile()));
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Symbolic links are not supported: " + e);
        }
    }

    @Test
    void shutdownHookCanBeRemovedBeforeAControlledExit() {
        AtomicInteger shutdownCalls = new AtomicInteger();
        CommonSetup.setupShutdownHandler(resultHandler -> {
            shutdownCalls.incrementAndGet();
            resultHandler.handleResult();
        });

        assertTrue(CommonSetup.removeShutdownHook());
        assertFalse(CommonSetup.removeShutdownHook());
        assertEquals(0, shutdownCalls.get());
    }

    @Test
    void uncaughtExceptionIsNotForwardedToApplicationDuringJvmShutdown() {
        AtomicInteger handlerCalls = new AtomicInteger();
        UncaughtExceptionHandler handler = (throwable, doShutDown) -> handlerCalls.incrementAndGet();
        RuntimeException throwable = new RuntimeException("expected test exception");

        CommonSetup.notifyUncaughtExceptionHandler(handler, throwable);
        UserThread.executeAtShutdown(() -> {
        });
        CommonSetup.notifyUncaughtExceptionHandler(handler, throwable);

        assertEquals(1, handlerCalls.get());
    }
}

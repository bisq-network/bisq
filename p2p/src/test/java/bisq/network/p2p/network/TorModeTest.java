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

package bisq.network.p2p.network;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TorModeTest {
    @Test
    void deletesTheTorFilesButKeepsTheKeyAndTheConfiguration(@TempDir Path torDir) throws IOException {
        Files.createDirectories(torDir.resolve("hiddenservice"));
        Files.writeString(torDir.resolve("hiddenservice").resolve("private_key"), "key");
        Files.writeString(torDir.resolve("external_tor.config"), "UseExternalTor 0");
        Files.createDirectories(torDir.resolve("pluggable_transports").resolve("sub"));
        Files.writeString(torDir.resolve("pluggable_transports").resolve("sub").resolve("lyrebird"), "");
        Files.writeString(torDir.resolve("cached-microdescs"), "");

        TorMode.deleteTorFiles(torDir.toFile());

        Set<String> fileNames = Arrays.stream(torDir.toFile().list()).collect(Collectors.toSet());
        assertEquals(Set.of("hiddenservice", "external_tor.config"), fileNames);
        assertTrue(Files.exists(torDir.resolve("hiddenservice").resolve("private_key")));
    }

    @Test
    void theLogsOfTorAreInTheTorDirectoryOutsideTheDataDirectoryOfTor(@TempDir Path torDir) {
        List<File> logFiles = TorMode.getTorLogFiles(torDir.toFile());

        assertEquals(List.of(torDir.resolve("tor-stdout.log").toFile(),
                        torDir.resolve("tor-stdout.log.1").toFile(),
                        torDir.resolve("tor-stderr.log").toFile()),
                logFiles);
        Path torDataDir = TorMode.getTorDataDir(torDir.toFile()).toPath();
        logFiles.forEach(logFile -> assertFalse(logFile.toPath().startsWith(torDataDir)));
    }

    @Test
    void deletesASymbolicLinkButNotItsTarget(@TempDir Path dir) throws IOException {
        Path torDir = Files.createDirectories(dir.resolve("tor"));
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Files.writeString(outside.resolve("file"), "data of the user");
        try {
            Files.createSymbolicLink(torDir.resolve("cache"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Symbolic links are not supported: " + e);
        }

        TorMode.deleteTorFiles(torDir.toFile());

        assertEquals(0, torDir.toFile().list().length);
        assertEquals("data of the user", Files.readString(outside.resolve("file")));
    }
}

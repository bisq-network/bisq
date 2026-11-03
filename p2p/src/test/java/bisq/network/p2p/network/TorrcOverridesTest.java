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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorrcOverridesTest {
    @Test
    void parsesTheEntriesOfTheTorrcFile(@TempDir Path tempDir) throws IOException {
        File torrcFile = writeTorrcFile(tempDir,
                "# a comment",
                "",
                "  SocksPort   9150  ",
                "Short",
                "UseBridges 1",
                "UseBridges 0");

        Map<String, String> torrcOverrides = parseFromCommandLine(torrcFile, "");

        assertEquals(Map.of("SocksPort", "9150", "UseBridges", "0"), torrcOverrides);
    }

    @Test
    void optionsOverrideTheTorrcFile(@TempDir Path tempDir) throws IOException {
        File torrcFile = writeTorrcFile(tempDir, "SocksPort 9150", "Log notice stdout");

        Map<String, String> torrcOverrides = parseFromCommandLine(torrcFile, "SocksPort 9250, AvoidDiskWrites 1");

        assertEquals(List.of("SocksPort", "Log", "AvoidDiskWrites"), List.copyOf(torrcOverrides.keySet()));
        assertEquals("9250", torrcOverrides.get("SocksPort"));
        assertEquals("notice stdout", torrcOverrides.get("Log"));
        assertEquals("1", torrcOverrides.get("AvoidDiskWrites"));
    }

    @Test
    void ignoresAMissingTorrcFile(@TempDir Path tempDir) {
        File missingFile = tempDir.resolve("missing").toFile();

        assertEquals(Map.of("SocksPort", "9150"), parseFromCommandLine(missingFile, "SocksPort 9150"));
    }

    @Test
    void anInvalidOptionDropsTheOptionsBeforeIt() {
        Map<String, String> torrcOverrides = parseFromCommandLine(null, "SocksPort 9150,invalid,AvoidDiskWrites 1");

        assertEquals(Map.of("AvoidDiskWrites", "1"), torrcOverrides);
    }

    @Test
    void rejectsAKeyWithWhitespace(@TempDir Path tempDir) throws IOException {
        File torrcFile = writeTorrcFile(tempDir, "Log\tnotice stdout");

        assertThrows(IllegalArgumentException.class, () -> parseFromCommandLine(torrcFile, ""));
    }

    @Test
    void returnsNoEntriesWithoutFileAndOptions() {
        assertTrue(parseFromCommandLine(null, "").isEmpty());
    }

    // The Tor library refuses the entries of a config file which make Tor start a program or load a library. Only the
    // config file is the source of such an entry, not the command line.
    @Test
    void namesTheKeysOfTheEntriesWhichAConfigFileBrings(@TempDir Path tempDir) throws IOException {
        String torrcOptions = "AccelName engine, clienttransportplug obfs4 exec /usr/bin/lyrebird";
        File torrcFile = writeTorrcFile(tempDir, "ServerTransportPlugin obfs4 exec /usr/bin/lyrebird", "UseBridges 1");

        // The command line sets both options
        TorrcOverrides fromCommandLine = TorrcOverrides.parse(TorrcSettings.fromCommandLine(torrcFile, torrcOptions));
        assertEquals(Set.of(), fromCommandLine.keysFromConfigFile());
        assertEquals("engine", fromCommandLine.entries().get("AccelName"));
        // A config file sets the options
        assertEquals(Set.of("AccelName", "clienttransportplug"),
                TorrcOverrides.parse(new TorrcSettings(null, false, torrcOptions, false)).keysFromConfigFile());
        // The command line names the torrc file, but the options come from a config file
        assertEquals(Set.of("AccelName", "clienttransportplug"),
                TorrcOverrides.parse(new TorrcSettings(torrcFile, true, torrcOptions, false)).keysFromConfigFile());
        // A config file names the torrc file
        assertEquals(Set.of("ServerTransportPlugin", "UseBridges"),
                TorrcOverrides.parse(new TorrcSettings(torrcFile, false, "", true)).keysFromConfigFile());
    }

    // Tor gets the value of the command line, so the entry of the config file does not reach Tor.
    @Test
    void anEntryOfTheCommandLineReplacesTheEntryOfAConfigFileWithTheSameKey(@TempDir Path tempDir) throws IOException {
        File torrcFile = writeTorrcFile(tempDir, "AccelName engine", "UseBridges 1");

        TorrcOverrides torrcOverrides = TorrcOverrides.parse(new TorrcSettings(torrcFile, false, "AccelName other",
                true));

        assertEquals("other", torrcOverrides.entries().get("AccelName"));
        assertEquals(Set.of("UseBridges"), torrcOverrides.keysFromConfigFile());
    }

    private static Map<String, String> parseFromCommandLine(File torrcFile, String torrcOptions) {
        return TorrcOverrides.parse(TorrcSettings.fromCommandLine(torrcFile, torrcOptions)).entries();
    }

    private static File writeTorrcFile(Path dir, String... lines) throws IOException {
        Path torrcFile = dir.resolve("torrc");
        Files.write(torrcFile, List.of(lines), StandardCharsets.UTF_8);
        return torrcFile.toFile();
    }
}

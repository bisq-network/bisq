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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void acceptsEntriesWhichStartAProgramOnlyFromTheCommandLine(@TempDir Path tempDir) throws IOException {
        String torrcOptions = "ClientTransportPlugin obfs4 exec /usr/bin/lyrebird";
        File torrcFile = writeTorrcFile(tempDir, "ServerTransportPlugin obfs4 exec /usr/bin/lyrebird");

        assertEquals("obfs4 exec /usr/bin/lyrebird", parseFromCommandLine(null, torrcOptions)
                .get("ClientTransportPlugin"));
        assertEquals("obfs4 exec /usr/bin/lyrebird", parseFromCommandLine(torrcFile, "")
                .get("ServerTransportPlugin"));

        var fromConfigFileOptions = assertThrows(IllegalArgumentException.class,
                () -> TorrcOverrides.parse(new TorrcSettings(null, false, torrcOptions, false)));
        assertTrue(fromConfigFileOptions.getMessage().contains("ClientTransportPlugin"));
        assertTrue(fromConfigFileOptions.getMessage().contains("only on the command line"));
        // The command line names a torrc file, but the options come from a config file
        assertThrows(IllegalArgumentException.class,
                () -> TorrcOverrides.parse(new TorrcSettings(torrcFile, true, torrcOptions, false)));
        // A config file names the torrc file
        assertThrows(IllegalArgumentException.class,
                () -> TorrcOverrides.parse(new TorrcSettings(torrcFile, false, "", true)));
        // Other entries of a config file are accepted
        assertEquals(Map.of("AvoidDiskWrites", "1"),
                TorrcOverrides.parse(new TorrcSettings(null, false, "AvoidDiskWrites 1", false)));
    }

    @Test
    void entriesWhichStartAProgramAreFoundInAnyCaseAndAsAbbreviation() {
        // Tor ignores the case of option names and accepts the start of a name
        assertTrue(TorrcOverrides.startsAProgram("ClientTransportPlugin"));
        assertTrue(TorrcOverrides.startsAProgram("clienttransportplugin"));
        assertTrue(TorrcOverrides.startsAProgram("ClientTransportPlug"));
        assertTrue(TorrcOverrides.startsAProgram("SERVERTRANSPORT"));
        assertFalse(TorrcOverrides.startsAProgram("ClientOnly"));
        assertFalse(TorrcOverrides.startsAProgram("ClientTransportPluginX"));
        assertFalse(TorrcOverrides.startsAProgram("UseBridges"));
        assertFalse(TorrcOverrides.startsAProgram(""));
    }

    private static Map<String, String> parseFromCommandLine(File torrcFile, String torrcOptions) {
        return TorrcOverrides.parse(TorrcSettings.fromCommandLine(torrcFile, torrcOptions));
    }

    private static File writeTorrcFile(Path dir, String... lines) throws IOException {
        Path torrcFile = dir.resolve("torrc");
        Files.write(torrcFile, List.of(lines), StandardCharsets.UTF_8);
        return torrcFile.toFile();
    }
}

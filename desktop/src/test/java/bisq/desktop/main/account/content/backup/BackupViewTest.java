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

package bisq.desktop.main.account.content.backup;

import bisq.network.p2p.network.TorMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Zip log files" adds the logs of Tor, in which Tor names the bridges, to the logs of the node, in which the Tor
 * library names the bridge of a bootstrap warning, so both go into the zip without them.
 */
class BackupViewTest {
    private static final String BRIDGE_FINGERPRINT = "0123456789ABCDEF0123456789ABCDEF01234567";
    private static final String BRIDGE_ADDRESS = "192.0.2.77:44321";
    // The warning as Tor 0.4.9 writes it when it cannot reach a bridge
    private static final String BRIDGE_WARNING = "Oct 31 09:07:36.000 [warn] Problem bootstrapping. Stuck at 5% " +
            "(conn): Connecting to a relay. (Connection refused; CONNECTREFUSED; count 1; recommendation warn; host " +
            BRIDGE_FINGERPRINT + " at " + BRIDGE_ADDRESS + ")\n";
    // The event of that warning as the Tor library logs it into the log of the node
    private static final String BOOTSTRAP_WARNING_OF_THE_TOR_LIBRARY = "Tor bootstrap event: TorBootstrapEvent(" +
            "progress=5, tag=conn, summary=Connecting to a relay\" WARNING=\"Connection refused\" " +
            "REASON=CONNECTREFUSED COUNT=1 RECOMMENDATION=warn HOSTID=\"" + BRIDGE_FINGERPRINT + "\" HOSTADDR=\"" +
            BRIDGE_ADDRESS + ", timestamp=2026-10-31T10:35:54.240025810Z)\n";

    @Test
    void theZipGetsTheLogsOfTorWithoutTheBridges(@TempDir Path dir) throws IOException {
        Path nodeLogPath = dir.resolve("bisq.log");
        Files.writeString(nodeLogPath, "log of the node\n" + BOOTSTRAP_WARNING_OF_THE_TOR_LIBRARY);
        List<File> torLogFiles = TorMode.getTorLogFiles(dir.resolve("tor").toFile());
        Files.createDirectories(dir.resolve("tor"));
        // The standard output of Tor and the earlier log exist, the standard error does not
        Files.writeString(torLogFiles.get(0).toPath(), BRIDGE_WARNING);
        Files.writeString(torLogFiles.get(1).toPath(), BRIDGE_WARNING);
        Path zipPath = dir.resolve("logs.zip");

        BackupView.zipLogFiles(List.of(nodeLogPath.toFile()), torLogFiles, zipPath);

        Map<String, String> entries = readEntries(zipPath);
        String nodeLog = entries.get("bisq.log");
        assertTrue(nodeLog.startsWith("log of the node\n"), nodeLog);
        assertFalse(nodeLog.contains(BRIDGE_FINGERPRINT), nodeLog);
        assertFalse(nodeLog.contains(BRIDGE_ADDRESS), nodeLog);
        assertEquals(3, entries.size(), entries.keySet().toString());
        for (File torLogFile : torLogFiles.subList(0, 2)) {
            String torLog = entries.get(torLogFile.getName());
            assertFalse(torLog.contains(BRIDGE_FINGERPRINT), torLog);
            assertFalse(torLog.contains(BRIDGE_ADDRESS), torLog);
            assertTrue(torLog.contains("Connection refused"), torLog);
        }
    }

    private static Map<String, String> readEntries(Path zipPath) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                entries.put(zipEntry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}

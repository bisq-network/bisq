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

import bisq.common.app.InstanceLock;
import bisq.common.file.FileUtil;

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
import org.junit.jupiter.api.Timeout;
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
        String nextRecord = "Oct-31 10:36:00.000 [main] INFO  b.c.Node: Bitcoin node 192.0.2.8:8333";
        String startupException = "java.io.IOException: Embedded Tor exited. exitCode=1, log=/bisq/tor/tor.log, ";
        String nativeLogTail = "end-of-bridge-certificate\n" + BRIDGE_WARNING;
        Files.writeString(nodeLogPath, "log of the node\n" + BOOTSTRAP_WARNING_OF_THE_TOR_LIBRARY +
                startupException + "log=" + nativeLogTail + "\tat example.Frame.method(Frame.java:10)\n" +
                startupException + "log (written by sandy and by Tor in its sandbox, not verified by Bisq)=" +
                nativeLogTail + nextRecord);
        List<File> torLogFiles = TorMode.getTorLogFiles(dir.resolve("tor").toFile());
        Files.createDirectories(dir.resolve("tor"));
        // The log of Tor exists, the earlier log does not
        Files.writeString(torLogFiles.get(0).toPath(), BRIDGE_WARNING);
        Path zipPath = dir.resolve("logs.zip");

        BackupView.zipLogFiles(List.of(nodeLogPath.toFile()), torLogFiles, zipPath);

        Map<String, String> entries = readEntries(zipPath);
        String nodeLog = entries.get("bisq.log");
        assertTrue(nodeLog.startsWith("log of the node\n"), nodeLog);
        assertFalse(nodeLog.contains(BRIDGE_FINGERPRINT), nodeLog);
        assertFalse(nodeLog.contains(BRIDGE_ADDRESS), nodeLog);
        assertFalse(nodeLog.contains("end-of-bridge-certificate"), nodeLog);
        assertTrue(nodeLog.contains("exitCode=1"), nodeLog);
        assertTrue(nodeLog.contains("at example.Frame.method"), nodeLog);
        assertTrue(nodeLog.endsWith(nextRecord), nodeLog);
        assertEquals(2, entries.size(), entries.keySet().toString());
        String torLog = entries.get(torLogFiles.get(0).getName());
        assertFalse(torLog.contains(BRIDGE_FINGERPRINT), torLog);
        assertFalse(torLog.contains(BRIDGE_ADDRESS), torLog);
        assertTrue(torLog.contains("Connection refused"), torLog);
    }

    // On Linux and macOS closing any descriptor of the lock file releases the lock of the process. The lock is probed
    // from another process, because the JVM of the owner still reports it as held.
    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void theBackupKeepsTheInstanceLockOfTheRunningNode(@TempDir Path dir) throws Exception {
        Path dataDir = dir.resolve("data");
        Files.createDirectories(dataDir.resolve("btc_mainnet").resolve("wallet"));
        Files.writeString(dataDir.resolve("btc_mainnet").resolve("wallet").resolve("bisq_BTC.wallet"), "wallet");
        Path backupDir = dir.resolve("backup");
        try (var instanceLock = new InstanceLock(dataDir)) {
            assertTrue(instanceLock.tryLock());

            List<File> notCopied = FileUtil.copyDirectory(dataDir.toFile(), backupDir.toFile(),
                    BackupView.getEntriesLeftOutOfBackup(dataDir.toFile(), dataDir.resolve("tor").toFile()));

            assertFalse(canAnotherProcessAcquireInstanceLock(dataDir), "The backup must not release the lock");
            assertEquals(List.of(), notCopied);
            assertTrue(Files.isRegularFile(backupDir.resolve("btc_mainnet").resolve("wallet").resolve("bisq_BTC.wallet")));
            assertFalse(Files.exists(InstanceLock.getLockFile(backupDir)));
        }
    }

    private static boolean canAnotherProcessAcquireInstanceLock(Path dataDir) throws Exception {
        String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process probe = new ProcessBuilder(javaCommand, "-cp", System.getProperty("java.class.path"),
                InstanceLockProbe.class.getName(), dataDir.toString())
                .redirectErrorStream(true)
                .start();
        try {
            String output = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = probe.waitFor();
            assertTrue(exitCode == InstanceLockProbe.ACQUIRED || exitCode == InstanceLockProbe.REFUSED, output);
            return exitCode == InstanceLockProbe.ACQUIRED;
        } finally {
            probe.destroyForcibly();
        }
    }

    public static final class InstanceLockProbe {
        static final int ACQUIRED = 10;
        static final int REFUSED = 11;

        public static void main(String[] args) throws IOException {
            boolean acquired;
            try (var instanceLock = new InstanceLock(Path.of(args[0]))) {
                acquired = instanceLock.tryLock();
            }
            System.exit(acquired ? ACQUIRED : REFUSED);
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

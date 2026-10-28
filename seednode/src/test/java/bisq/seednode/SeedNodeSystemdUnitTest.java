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

package bisq.seednode;

import bisq.network.tor.ExternalTorConfig;
import bisq.network.tor.process.TorDirectories;

import bisq.common.config.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * By default the seed node starts its own Tor. For a running Tor, bisq.service writes its settings from bisq.env into
 * external_tor.config with a command of ExecStartPre. The tests run the commands of the unit as systemd does: systemd
 * passes the text between the quotes to bash and leaves variables in the form $NAME to bash. ${...} systemd replaces
 * itself, and an expression of bash such as ${NAME,,} becomes an empty string, so the commands must not use it.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class SeedNodeSystemdUnitTest {
    private static final String EXEC_START_PRE_BASH = "ExecStartPre=/bin/bash -c \"";
    private static final String EXEC_START_PRE_BASH_SINGLE_QUOTED = "ExecStartPre=/bin/bash -c '";
    private static final String EXEC_START_PRE_BASH_AS_ROOT = "ExecStartPre=+/bin/bash -c \"";
    private static final String EXEC_STOP_POST_BASH_AS_ROOT = "ExecStopPost=+/bin/bash -c \"";

    @Test
    void runsItsOwnTorByDefault(@TempDir Path bisqHome) throws Exception {
        Map<String, String> environment = readBisqEnv(bisqHome);

        runExternalTorConfigCommand(environment);

        assertEquals("-1", environment.get("BISQ_EXTERNAL_TOR_PORT"));
        assertFalse(Files.exists(getExternalTorConfigPathOfTheNode(environment)));
    }

    // Without the tor package, a unit which requires a unit of the tor service would not start.
    @Test
    void doesNotRequireTheTorServiceOfTheSystem() throws IOException {
        List<String> torServiceLines = Files.readAllLines(Path.of("bisq.service")).stream()
                .filter(line -> !line.startsWith("#"))
                .filter(line -> line.contains("tor@") || line.contains("tor.service"))
                .toList();

        assertEquals(List.of(), torServiceLines);
    }

    @Test
    void writesTheRunningTorOfBisqEnvIntoTheTorDirectoryOfTheNode(@TempDir Path bisqHome) throws Exception {
        Map<String, String> environment = readBisqEnv(bisqHome);
        environment.put("BISQ_EXTERNAL_TOR_PORT", "9051");

        runExternalTorConfigCommand(environment);

        ExternalTorConfig externalTorConfig = ExternalTorConfig.parse(
                Files.readString(getExternalTorConfigPathOfTheNode(environment)));
        assertEquals(Optional.of(true), externalTorConfig.getUseExternalTor());
        assertEquals("127.0.0.1", externalTorConfig.getControlHost());
        assertEquals(9051, externalTorConfig.getControlPort());
        assertTrue(externalTorConfig.isCookieAuthentication());
        assertEquals(Optional.of(Path.of(environment.get("BISQ_EXTERNAL_TOR_CONTROL_COOKIE_FILE"))),
                externalTorConfig.getCookieAuthFile());
    }

    @Test
    void removesTheFileWhenTheNodeRunsItsOwnTor(@TempDir Path bisqHome) throws Exception {
        Map<String, String> environment = readBisqEnv(bisqHome);
        environment.put("BISQ_EXTERNAL_TOR_PORT", "9051");
        runExternalTorConfigCommand(environment);
        assertTrue(Files.exists(getExternalTorConfigPathOfTheNode(environment)));

        environment.put("BISQ_EXTERNAL_TOR_PORT", "-1");
        runExternalTorConfigCommand(environment);

        assertFalse(Files.exists(getExternalTorConfigPathOfTheNode(environment)));
    }

    // --baseCurrencyNetwork takes the network in any case, and the node lower-cases it for its directory
    @Test
    void writesTheRunningTorIntoTheTorDirectoryOfTheNodeForANetworkInUpperCase(@TempDir Path bisqHome)
            throws Exception {
        Map<String, String> environment = readBisqEnv(bisqHome);
        environment.put("BISQ_BASE_CURRENCY", "BTC_MAINNET");
        environment.put("BISQ_EXTERNAL_TOR_PORT", "9051");

        runExternalTorConfigCommand(environment);

        assertTrue(Files.exists(getExternalTorConfigPathOfTheNode(environment)));
    }

    // The stubs of mount and umount record the directory, which is the last argument of both
    @Test
    void mountsTheTmpfsForTheDaoJsonFilesOnTheJsonDirectoryOfTheNode(@TempDir Path bisqHome, @TempDir Path stubs)
            throws Exception {
        Map<String, String> environment = readBisqEnv(bisqHome);
        environment.put("BISQ_BASE_CURRENCY", "BTC_MAINNET");
        environment.put("BISQ_DUMP_BLOCKCHAIN", "true");
        Path calls = stubs.resolve("calls");
        for (String program : List.of("mount", "umount")) {
            Path stub = stubs.resolve(program);
            Files.writeString(stub, "#!/bin/bash\necho \"" + program + " ${@: -1}\" >> " + calls + "\n");
            assertTrue(stub.toFile().setExecutable(true));
        }
        environment.put("PATH", stubs + ":" + environment.get("PATH"));

        runUnitCommand(EXEC_START_PRE_BASH_AS_ROOT, "db/json", environment);
        runUnitCommand(EXEC_STOP_POST_BASH_AS_ROOT, "db/json", environment);

        String jsonDir = new File(createConfigOfTheNode(environment).storageDir, "json").getPath();
        assertEquals(List.of("mount " + jsonDir, "umount " + jsonDir), Files.readAllLines(calls));
    }

    // Every local user can read the command line of a process in /proc
    @Test
    void passesNoSecretOnTheCommandLineOfTheNode() throws IOException {
        String execStart = String.join(" ", readExecStart());

        for (String secret : List.of("rpcPassword", "BITCOIN_RPC_PASS", "bmOracleNodePrivKey",
                "BISQ_BM_ORACLE_NODE_PRIVKEY")) {
            assertFalse(execStart.contains(secret), secret);
        }
    }

    @Test
    void writesTheSecretsIntoTheConfigFileOfTheNodeForItsUserOnly(@TempDir Path bisqHome) throws Exception {
        Map<String, String> environment = readBisqEnv(bisqHome);
        environment.put("BITCOIN_RPC_PASS", "pass word*with=signs");
        environment.put("BISQ_BM_ORACLE_NODE_PRIVKEY", "0123456789abcdef");
        Path configFile = bisqHome.resolve(environment.get("BISQ_APP_NAME")).resolve("bisq.properties");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "useTorSandbox=false\n  rpcPassword=old\nbmOracleNodePrivKey=old\n");
        Files.setPosixFilePermissions(configFile, PosixFilePermissions.fromString("rw-r--r--"));

        // The unit runs the command at every start
        runUnitCommand(EXEC_START_PRE_BASH_SINGLE_QUOTED, "bisq.properties", environment);
        runUnitCommand(EXEC_START_PRE_BASH_SINGLE_QUOTED, "bisq.properties", environment);

        assertEquals(List.of("useTorSandbox=false", "rpcPassword=pass word*with=signs",
                "bmOracleNodePrivKey=0123456789abcdef"), Files.readAllLines(configFile));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(configFile));
        Config config = createConfigOfTheNode(environment);
        assertEquals("pass word*with=signs", config.rpcPassword);
        assertEquals("0123456789abcdef", config.bmOracleNodePrivKey);
        assertFalse(config.useTorSandbox);
    }

    private static Map<String, String> readBisqEnv(Path bisqHome) throws IOException {
        Map<String, String> environment = new HashMap<>();
        Files.readAllLines(Path.of("bisq.env")).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(line -> {
                    int delimiter = line.indexOf('=');
                    environment.put(line.substring(0, delimiter), line.substring(delimiter + 1));
                });
        environment.put("BISQ_HOME", bisqHome.toString());
        environment.put("PATH", "/usr/bin:/bin");
        return environment;
    }

    private static void runExternalTorConfigCommand(Map<String, String> environment) throws Exception {
        runUnitCommand(EXEC_START_PRE_BASH, "external_tor.config", environment);
    }

    // The prefix ends with the quote which encloses the command
    private static void runUnitCommand(String commandPrefix, String marker, Map<String, String> environment)
            throws Exception {
        String unitLine = Files.readAllLines(Path.of("bisq.service")).stream()
                .filter(line -> line.startsWith(commandPrefix) && line.contains(marker))
                .findFirst()
                .orElseThrow();
        assertTrue(unitLine.endsWith(commandPrefix.substring(commandPrefix.length() - 1)));
        String command = unitLine.substring(commandPrefix.length(), unitLine.length() - 1);
        assertFalse(command.contains("${"), command);

        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.environment().clear();
        processBuilder.environment().putAll(environment);
        Process process = processBuilder.start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
    }

    // The line of ExecStart and the lines which continue it
    private static List<String> readExecStart() throws IOException {
        List<String> execStart = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("bisq.service"))) {
            if (line.startsWith("ExecStart=") || (!execStart.isEmpty() &&
                    execStart.get(execStart.size() - 1).endsWith("\\"))) {
                execStart.add(line);
            }
        }
        assertFalse(execStart.isEmpty());
        return execStart;
    }

    // The Tor directory which the node gets from the options of ExecStart
    private static Path getExternalTorConfigPathOfTheNode(Map<String, String> environment) {
        return new TorDirectories(createConfigOfTheNode(environment).torDir.toPath()).getExternalTorConfigPath();
    }

    // The directories of the node as it derives them from the options of ExecStart
    private static Config createConfigOfTheNode(Map<String, String> environment) {
        return new Config("--" + Config.USER_DATA_DIR + "=" + environment.get("BISQ_HOME"),
                "--" + Config.APP_NAME + "=" + environment.get("BISQ_APP_NAME"),
                "--" + Config.BASE_CURRENCY_NETWORK + "=" + environment.get("BISQ_BASE_CURRENCY"));
    }
}

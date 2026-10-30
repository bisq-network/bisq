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

import java.io.IOException;

import java.util.HashMap;
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
 * bisq.service writes the settings of the system Tor from bisq.env into external_tor.config with a command of
 * ExecStartPre. The tests run that command as systemd does: systemd passes the text between the quotes to bash and
 * leaves the variables to bash.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class SeedNodeSystemdUnitTest {
    private static final String EXEC_START_PRE_BASH = "ExecStartPre=/bin/bash -c \"";

    @Test
    void writesTheSystemTorOfBisqEnvIntoTheTorDirectoryOfTheNode(@TempDir Path bisqHome) throws Exception {
        Map<String, String> environment = readBisqEnv(bisqHome);

        runExternalTorConfigCommand(environment);

        ExternalTorConfig externalTorConfig = ExternalTorConfig.parse(
                Files.readString(getExternalTorConfigPathOfTheNode(environment)));
        assertEquals(Optional.of(true), externalTorConfig.getUseExternalTor());
        assertEquals("127.0.0.1", externalTorConfig.getControlHost());
        assertEquals(Integer.parseInt(environment.get("BISQ_EXTERNAL_TOR_PORT")), externalTorConfig.getControlPort());
        assertTrue(externalTorConfig.isCookieAuthentication());
        assertEquals(Optional.of(Path.of(environment.get("BISQ_EXTERNAL_TOR_CONTROL_COOKIE_FILE"))),
                externalTorConfig.getCookieAuthFile());
    }

    @Test
    void removesTheFileWhenTheNodeRunsItsOwnTor(@TempDir Path bisqHome) throws Exception {
        Map<String, String> environment = readBisqEnv(bisqHome);
        runExternalTorConfigCommand(environment);
        assertTrue(Files.exists(getExternalTorConfigPathOfTheNode(environment)));

        environment.put("BISQ_EXTERNAL_TOR_PORT", "-1");
        runExternalTorConfigCommand(environment);

        assertFalse(Files.exists(getExternalTorConfigPathOfTheNode(environment)));
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
        String unitLine = Files.readAllLines(Path.of("bisq.service")).stream()
                .filter(line -> line.startsWith(EXEC_START_PRE_BASH) && line.contains("external_tor.config"))
                .findFirst()
                .orElseThrow();
        assertTrue(unitLine.endsWith("\""));
        String command = unitLine.substring(EXEC_START_PRE_BASH.length(), unitLine.length() - 1);

        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.environment().clear();
        processBuilder.environment().putAll(environment);
        Process process = processBuilder.start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
    }

    // The Tor directory which the node gets from the options of ExecStart
    private static Path getExternalTorConfigPathOfTheNode(Map<String, String> environment) {
        Config config = new Config("--" + Config.USER_DATA_DIR + "=" + environment.get("BISQ_HOME"),
                "--" + Config.APP_NAME + "=" + environment.get("BISQ_APP_NAME"),
                "--" + Config.BASE_CURRENCY_NETWORK + "=" + environment.get("BISQ_BASE_CURRENCY"));
        return new TorDirectories(config.torDir.toPath()).getExternalTorConfigPath();
    }
}

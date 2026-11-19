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

package bisq.apitest.method;

import bisq.apitest.dao.DaoTestUtils;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daemon refuses to start without an api password: it exits with a config error
 * before it starts Tor, the P2P network or the wallet. Up to v1.10.11 it kept running,
 * and only the start of the gRPC server failed.
 *
 * <p>Runs the daemon binary of Alice's image in a throw-away container without network
 * and without the stack's entrypoint, which itself refuses an empty password. A daemon
 * that starts anyway does not exit, so the run has a time limit and the container is
 * removed in any case.
 */
@Slf4j
public class ApiPasswordRequiredAtStartTest extends DockerMethodTest {
    private static final String CONFIG_ERROR =
            "error: The 'apiPassword' option must be set. The gRPC API does not start without a password.";
    private static final Duration EXIT_TIMEOUT = Duration.ofSeconds(90);

    @Test
    public void testDaemonRefusesToStartWithoutPassword() {
        assertRefusedAtStart("no-password", List.of());
    }

    @Test
    public void testDaemonRefusesToStartWithBlankPassword() {
        assertRefusedAtStart("blank-password", List.of("--apiPassword= "));
    }

    private static void assertRefusedAtStart(String label, List<String> extraArgs) {
        String alicesImage = DaoTestUtils.runDocker(List.of("inspect", "--format", "{{.Config.Image}}",
                System.getProperty("aliceContainer", "alice"))).trim();
        String container = "apitest-" + label + "-" + System.nanoTime();
        List<String> args = new ArrayList<>(List.of("run", "--name", container, "--network", "none",
                "--entrypoint", "/bisq/daemon/bin/daemon", alicesImage,
                "--baseCurrencyNetwork=BTC_REGTEST",
                "--appName=bisq-BTC_REGTEST_" + label,
                "--appDataDir=/tmp/" + label));
        args.addAll(extraArgs);
        try {
            IllegalStateException exception = assertTimeoutPreemptively(EXIT_TIMEOUT,
                    () -> assertThrows(IllegalStateException.class, () -> DaoTestUtils.runDocker(args)),
                    "daemon (" + label + ") did not exit");
            String output = exception.getMessage();
            assertTrue(output.contains(CONFIG_ERROR), "missing config error (" + label + "): " + output);
        } finally {
            try {
                DaoTestUtils.runDocker(List.of("rm", "--force", container));
            } catch (IllegalStateException ex) {
                log.warn("Could not remove container {}", container, ex);
            }
        }
    }
}

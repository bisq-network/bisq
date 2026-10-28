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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.btcnodemonitor.socksProxy;

import bisq.network.tor.TorSandboxConfig;
import bisq.network.tor.TorService;
import bisq.network.tor.TorTransportConfig;

import bisq.common.config.Config;

import java.nio.file.Path;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class ProxySetupTest {
    @Test
    void runsTorInTheSandboxByDefault(@TempDir Path userDataDir) {
        TorSandboxConfig sandboxConfig = getSandboxConfig(new Config("--" + Config.USER_DATA_DIR + "=" + userDataDir));

        assertTrue(sandboxConfig.isEnabled());
        // The refusal of an unusable sandbox names the option of the monitor which turns it off.
        assertTrue(sandboxConfig.getOptOutHint().contains("--" + Config.USE_TOR_SANDBOX + "=false"));
    }

    @Test
    void runsTorWithoutTheSandboxWhenTheOptionTurnsItOff(@TempDir Path userDataDir) {
        TorSandboxConfig sandboxConfig = getSandboxConfig(new Config("--" + Config.USER_DATA_DIR + "=" + userDataDir,
                "--" + Config.USE_TOR_SANDBOX + "=false"));

        assertFalse(sandboxConfig.isEnabled());
    }

    private static TorSandboxConfig getSandboxConfig(Config config) {
        var transportConfig = new AtomicReference<TorTransportConfig>();
        try (var construction = mockConstruction(TorService.class, (mock, context) -> {
            transportConfig.set((TorTransportConfig) context.arguments().get(0));
            when(mock.initialize()).thenReturn(CompletableFuture.completedFuture(true));
        })) {
            new ProxySetup(config).createTorMode().getTor();
        }
        return transportConfig.get().getSandboxConfig();
    }
}

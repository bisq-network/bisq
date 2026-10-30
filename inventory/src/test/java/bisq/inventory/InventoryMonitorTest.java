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

package bisq.inventory;

import bisq.common.config.BaseCurrencyNetwork;

import java.io.File;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryMonitorTest {
    // InventoryMonitorMain passes a handler which shuts the monitor down with the failure exit status
    @Test
    void failedStartOfTorRunsTheSetupFailedHandler(@TempDir File appDir) {
        AtomicInteger setupFailedHandlerCalls = new AtomicInteger();
        InventoryMonitor inventoryMonitor = new InventoryMonitor(appDir, true, BaseCurrencyNetwork.BTC_REGTEST,
                120, false, true, setupFailedHandlerCalls::incrementAndGet);

        inventoryMonitor.onSetupFailed(new RuntimeException("Tor did not start"));

        assertEquals(1, setupFailedHandlerCalls.get());
    }

    // The monitor keeps a directory of JSON files for each seed node of the file of its network
    @Test
    void findsTheSeedNodesOfItsNetworkAlsoInATurkishDefaultLocale(@TempDir File appDir) {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            new InventoryMonitor(appDir, true, BaseCurrencyNetwork.BTC_MAINNET, 120, false, true, () -> {
            });
        } finally {
            Locale.setDefault(defaultLocale);
        }

        String[] seedNodeJsonDirs = new File(appDir, "json").list();
        assertNotNull(seedNodeJsonDirs);
        assertTrue(seedNodeJsonDirs.length > 0);
    }
}

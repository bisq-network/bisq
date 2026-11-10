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

package bisq.desktop.main.overlays.windows;

import org.junit.jupiter.api.Test;

import static bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow.getPluggableTransportsNotAllowedMessageKey;
import static bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow.offersToTurnOffSandboxForTransports;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorNetworkSettingsWindowTest {
    private static final String KEY = "torNetworkSettingWindow.pluggableTransportsNotAllowed.msg";

    // On Windows only turning the sandbox off makes these bridges work
    @Test
    void offersToTurnOffTheSandboxForTheTransportsOnlyOnWindows() {
        assertTrue(offersToTurnOffSandboxForTransports(true, false));
        assertEquals(KEY + ".windows.turnOff", getPluggableTransportsNotAllowedMessageKey(true, false, false));

        assertFalse(offersToTurnOffSandboxForTransports(false, false));
        assertEquals(KEY, getPluggableTransportsNotAllowedMessageKey(false, false, false));
    }

    // The option on the command line overrides what the button writes into the config file
    @Test
    void offersNotToTurnOffTheSandboxWhileTheCommandLineSetsIt() {
        assertFalse(offersToTurnOffSandboxForTransports(true, true));
        assertEquals(KEY + ".windows", getPluggableTransportsNotAllowedMessageKey(true, true, false));
    }

    @Test
    void namesTheOptionOfTheTransportsWhileTheCommandLineSetsIt() {
        assertEquals(KEY + ".commandLine", getPluggableTransportsNotAllowedMessageKey(false, false, true));
        // On Windows the switch is disabled anyway
        assertEquals(KEY + ".windows.turnOff", getPluggableTransportsNotAllowedMessageKey(true, false, true));
    }
}

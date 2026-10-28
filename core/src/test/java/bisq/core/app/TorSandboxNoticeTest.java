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

package bisq.core.app;

import bisq.core.locale.Res;

import bisq.network.tor.TorSandboxStatus;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorSandboxNoticeTest {
    private static final String HOST_NETWORK = "popup.torSandbox.weakLevel.hostNetwork";
    private static final String SIGNALS_NOT_ISOLATED = "popup.torSandbox.weakLevel.signalsNotIsolated";

    @BeforeEach
    void setUp() {
        Res.setup();
    }

    // The user turned the sandbox off or configured a running Tor, so the user chose it
    @Test
    void noNoticeWithoutTheSandbox() {
        assertEquals(Optional.empty(), TorSandboxNotice.from(TorSandboxStatus.DISABLED));
        assertEquals(Optional.empty(), TorSandboxNotice.from(TorSandboxStatus.EXTERNAL_TOR));
        assertEquals(Optional.empty(), TorSandboxNotice.from(TorSandboxStatus.NOT_STARTED));
    }

    @Test
    void noNoticeForALevelWithItsOwnNetworkAndIsolatedSignals() {
        assertEquals(Optional.empty(), TorSandboxNotice.from(TorSandboxStatus.sandboxed("base", true, true)));
        assertEquals(Optional.empty(), TorSandboxNotice.from(TorSandboxStatus.sandboxed("contained", true, true)));
    }

    @Test
    void noticeForTheNetworkOfTheComputer() {
        TorSandboxNotice notice = TorSandboxNotice.from(TorSandboxStatus.sandboxed("host-network", false, true))
                .orElseThrow();

        assertEquals("torSandboxWeakLevelHostNetwork", notice.getDontShowAgainKey());
        assertTrue(notice.getMessage().contains("host-network"));
        assertTrue(notice.getMessage().contains(Res.get(HOST_NETWORK)));
        assertFalse(notice.getMessage().contains(Res.get(SIGNALS_NOT_ISOLATED)));
    }

    @Test
    void noticeForSignalsWhichTheSandboxDoesNotIsolate() {
        TorSandboxNotice notice = TorSandboxNotice.from(TorSandboxStatus.sandboxed("level", true, false))
                .orElseThrow();

        assertEquals("torSandboxWeakLevelSignalsNotIsolated", notice.getDontShowAgainKey());
        assertTrue(notice.getMessage().contains(Res.get(SIGNALS_NOT_ISOLATED)));
        assertFalse(notice.getMessage().contains(Res.get(HOST_NETWORK)));
    }

    // "Don't show again" for one weakness must not hide a combination which the user did not see yet
    @Test
    void oneNoticeWithAKeyOfItsOwnForBothWeaknesses() {
        TorSandboxNotice notice = TorSandboxNotice.from(
                TorSandboxStatus.sandboxed("host-network-signals", false, false)).orElseThrow();

        assertEquals("torSandboxWeakLevelHostNetworkSignalsNotIsolated", notice.getDontShowAgainKey());
        assertTrue(notice.getMessage().contains("host-network-signals"));
        assertTrue(notice.getMessage().contains(Res.get(HOST_NETWORK)));
        assertTrue(notice.getMessage().contains(Res.get(SIGNALS_NOT_ISOLATED)));
    }
}

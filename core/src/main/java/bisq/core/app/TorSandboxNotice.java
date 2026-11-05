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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * The notice for a Tor which runs in a level of its sandbox that gives up a protection on this system: the network of
 * its own or the isolation of its signals. A sandbox which the user turned off and a Tor which Bisq did not start get
 * no notice, because the user chose them.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class TorSandboxNotice {
    // One key per combination of weaknesses, so that "Don't show again" does not hide a weakness which the user did
    // not see yet
    private final String dontShowAgainKey;
    private final String message;

    private TorSandboxNotice(String dontShowAgainKey, String message) {
        this.dontShowAgainKey = dontShowAgainKey;
        this.message = message;
    }

    public static Optional<TorSandboxNotice> from(TorSandboxStatus status) {
        if (!status.isSandboxed() || !(status.isHostNetwork() || status.isSignalsNotIsolated())) {
            return Optional.empty();
        }

        StringBuilder dontShowAgainKey = new StringBuilder("torSandboxWeakLevel");
        List<String> weaknesses = new ArrayList<>();
        if (status.isHostNetwork()) {
            dontShowAgainKey.append("HostNetwork");
            weaknesses.add(Res.get("popup.torSandbox.weakLevel.hostNetwork"));
        }
        if (status.isSignalsNotIsolated()) {
            dontShowAgainKey.append("SignalsNotIsolated");
            weaknesses.add(Res.get("popup.torSandbox.weakLevel.signalsNotIsolated"));
        }
        String message = Res.get("popup.torSandbox.weakLevel.msg", status.getLevel(), String.join("\n\n", weaknesses));
        return Optional.of(new TorSandboxNotice(dontShowAgainKey.toString(), message));
    }
}

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

package bisq.network.p2p.network;

import bisq.network.tor.sandbox.PluggableTransportsNotAllowedException;

import com.google.common.base.Throwables;

import java.util.List;

/**
 * A failed start of Tor which other bridges, or allowing their pluggable transports, can fix. The desktop application
 * shows it in a popup which leads to the fix.
 */
public class BridgesCanHelpException extends RuntimeException {
    public BridgesCanHelpException(String message, Throwable cause) {
        super(message, cause);
    }

    // The pluggable transports of the bridges which the sandbox refused, empty for the other failures
    public List<String> getNotAllowedTransportNames() {
        return Throwables.getCausalChain(this).stream()
                .filter(PluggableTransportsNotAllowedException.class::isInstance)
                .map(cause -> ((PluggableTransportsNotAllowedException) cause).getTransportNames())
                .findFirst()
                .orElse(List.of());
    }
}

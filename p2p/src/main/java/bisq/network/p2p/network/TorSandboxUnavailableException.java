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

/**
 * A failed start of Tor because the node cannot run it in the sandbox, which turning the sandbox off is one way to fix.
 * The node decides this from its own configuration, the exit status of sandy and the status record of sandy, which a
 * sandboxed Tor cannot write, so that a compromised Tor cannot make the desktop application offer to turn it off.
 */
public class TorSandboxUnavailableException extends ExplainedStartFailureException {
    public TorSandboxUnavailableException(String message) {
        super(message);
    }
}

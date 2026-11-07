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
 * A failed start of Tor because the node cannot run it in the sandbox on this system, which turning the sandbox off
 * fixes. The node decides this itself, never from what a sandboxed Tor wrote, so that a compromised Tor cannot make the
 * desktop application offer to turn the sandbox off.
 */
public class TorSandboxUnavailableException extends ExplainedStartFailureException {
    public TorSandboxUnavailableException(String message) {
        super(message);
    }
}

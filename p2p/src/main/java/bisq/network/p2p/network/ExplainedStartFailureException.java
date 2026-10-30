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
 * A failed start of Tor whose message says why it failed and what the user can do, for example turn the sandbox off,
 * fix the running Tor or run Bisq on a Whonix-Workstation. The desktop application shows the message without its
 * generic advice to check the internet connection or to restart, which does not help with these failures.
 */
public class ExplainedStartFailureException extends RuntimeException {
    public ExplainedStartFailureException(String message) {
        super(message);
    }
}

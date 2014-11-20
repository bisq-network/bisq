/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.network;

public enum BootstrapState {
    PEER_CREATION_FAILED,
    DISCOVERY_STARTED,
    DISCOVERY_DIRECT_SUCCEEDED,
    DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED,
    DISCOVERY_FAILED,
    DISCOVERY_AUTO_PORT_FORWARDING_STARTED,
    DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED,
    DISCOVERY_AUTO_PORT_FORWARDING_FAILED,
    RELAY_STARTED,
    RELAY_SUCCEEDED,
    RELAY_FAILED,
    BOOT_STRAP_FAILED;

    private String message;

    BootstrapState() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

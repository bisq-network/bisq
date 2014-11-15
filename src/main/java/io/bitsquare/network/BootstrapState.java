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

/**
 * NOT_SUCCEEDED means we will try the next step, FAILED is used for fatal failures which will terminate the bootstrap
 */
public enum BootstrapState {
    PEER_CREATION,
    PEER_CREATION_FAILED,
    DIRECT_INIT,
    DIRECT_SUCCESS,
    DIRECT_NOT_SUCCEEDED,
    DIRECT_FAILED,
    MANUAL_PORT_FORWARDING_SUCCESS,
    AUTO_PORT_FORWARDING_INIT,
    AUTO_PORT_FORWARDING_SETUP_DONE,
    AUTO_PORT_FORWARDING_SUCCESS,
    AUTO_PORT_FORWARDING_NOT_SUCCEEDED,
    AUTO_PORT_FORWARDING_FAILED,
    RELAY_INIT,
    RELAY_SUCCESS,
    RELAY_FAILED;

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

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

public enum CloseConnectionReason {
    // First block are from different exceptions
    SOCKET_CLOSED(false, false),
    RESET(false, false),
    SOCKET_TIMEOUT(false, false),
    TERMINATED(false, false),
    // EOFException
    CORRUPTED_DATA(false, false),
    NO_PROTO_BUFFER_DATA(false, false),
    NO_PROTO_BUFFER_ENV(false, false),
    UNKNOWN_EXCEPTION(false, false),

    // Planned
    APP_SHUT_DOWN(true, true),
    CLOSE_REQUESTED_BY_PEER(false, true),

    // send msg
    SEND_MSG_FAILURE(false, false),
    SEND_MSG_TIMEOUT(false, false),

    // maintenance
    TOO_MANY_CONNECTIONS_OPEN(true, true),
    TOO_MANY_SEED_NODES_CONNECTED(true, true),
    UNKNOWN_PEER_ADDRESS(true, true),

    // illegal requests
    RULE_VIOLATION(true, false),
    PEER_BANNED(true, false),
    INVALID_CLASS_RECEIVED(false, false),
    MANDATORY_CAPABILITIES_NOT_SUPPORTED(false, false);

    public final boolean sendCloseMessage;
    public final boolean isIntended;

    CloseConnectionReason(boolean sendCloseMessage, boolean isIntended) {
        this.sendCloseMessage = sendCloseMessage;
        this.isIntended = isIntended;
    }

    @Override
    public String toString() {
        return "CloseConnectionReason{" +
                "sendCloseMessage=" + sendCloseMessage +
                ", isIntended=" + isIntended +
                "} " + super.toString();
    }
}

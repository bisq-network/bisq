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

public enum RuleViolation {
    INVALID_DATA_TYPE(2),
    WRONG_NETWORK_ID(0),
    MAX_MSG_SIZE_EXCEEDED(2),
    THROTTLE_LIMIT_EXCEEDED(2),
    TOO_MANY_REPORTED_PEERS_SENT(2),
    PEER_BANNED(0),
    INVALID_CLASS(0);

    public final int maxTolerance;

    RuleViolation(int maxTolerance) {
        this.maxTolerance = maxTolerance;
    }
}

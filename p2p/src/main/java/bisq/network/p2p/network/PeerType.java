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

public enum PeerType {
    // PEER is default type
    PEER,
    // If connection was used for initial data request/response. Those are marked with the InitialDataExchangeMessage interface
    INITIAL_DATA_EXCHANGE,
    // If a PrefixedSealedAndSignedMessage was sent (usually a trade message). Expects that node address is known.
    DIRECT_MSG_PEER
}

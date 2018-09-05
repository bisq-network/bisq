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

package bisq.network.p2p.storage.payload;


import bisq.network.p2p.NodeAddress;

import bisq.common.proto.network.NetworkPayload;

/**
 * Used for network_messages which require that the data owner is online.
 * <p/>
 * This is used for the offers to avoid dead offers in case the maker is in standby mode or the app has
 * terminated without sending the remove message (e.g. network connection lost or in case of a crash).
 */
public interface RequiresOwnerIsOnlinePayload extends NetworkPayload {
    /**
     * @return NodeAddress of the data owner
     */
    NodeAddress getOwnerNodeAddress();
}

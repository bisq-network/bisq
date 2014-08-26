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

package io.bitsquare.msg.listeners;

import java.util.Map;

import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;

public interface OrderBookListener {
    void onOfferAdded(Data offerData, boolean success);

    void onOffersReceived(Map<Number640, Data> dataMap, boolean success);

    void onOfferRemoved(Data data, boolean success);
}
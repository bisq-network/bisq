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

package io.bitsquare.trade.protocol.offer.messages;

import io.bitsquare.trade.protocol.trade.OfferMessage;

import java.io.Serializable;

// That msg is used to ping the offerer if he is online and if the offer is still available
public class RequestIsOfferAvailableMessage implements Serializable, OfferMessage {
    private static final long serialVersionUID = 4630151440192191798L;
    private final String offerId;

    public RequestIsOfferAvailableMessage(String offerId) {
        this.offerId = offerId;
    }

    @Override
    public String getOfferId() {
        return offerId;
    }


}

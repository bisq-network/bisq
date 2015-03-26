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

package io.bitsquare.trade.protocol.availability;

import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.AddressService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.protocol.trade.messages.OfferMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckOfferAvailabilityModel extends Model {
    private static final Logger log = LoggerFactory.getLogger(CheckOfferAvailabilityModel.class);

    public final Offer offer;
    public final MessageService messageService;
    public final AddressService addressService;

    private Peer peer;
    private OfferMessage message;

    public CheckOfferAvailabilityModel(Offer offer, MessageService messageService, AddressService addressService) {
        this.offer = offer;
        this.messageService = messageService;
        this.addressService = addressService;
    }

    public Peer getPeer() {
        return peer;
    }

    public void setPeer(Peer peer) {
        this.peer = peer;
    }

    public void setMessage(OfferMessage message) {
        this.message = message;
    }

    public OfferMessage getMessage() {
        return message;
    }
}

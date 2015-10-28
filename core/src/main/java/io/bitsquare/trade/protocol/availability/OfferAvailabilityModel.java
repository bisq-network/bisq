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

import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.availability.messages.OfferMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfferAvailabilityModel implements Model {
    private static final Logger log = LoggerFactory.getLogger(OfferAvailabilityModel.class);

    public final Offer offer;
    public final PubKeyRing pubKeyRing;
    public final P2PService p2PService;

    private Address peerAddress;
    private OfferMessage message;

    public OfferAvailabilityModel(Offer offer,
                                  PubKeyRing pubKeyRing,
                                  P2PService p2PService) {
        this.offer = offer;
        this.pubKeyRing = pubKeyRing;
        this.p2PService = p2PService;
    }

    public Address getPeerAddress() {
        return peerAddress;
    }

    public void setPeerAddress(Address peerAddress) {
        this.peerAddress = peerAddress;
    }

    public void setMessage(OfferMessage message) {
        this.message = message;
    }

    public OfferMessage getMessage() {
        return message;
    }

    @Override
    public void persist() {
    }

    @Override
    public void onComplete() {
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }
}

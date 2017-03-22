/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.offer.availability;

import io.bisq.common.taskrunner.Model;
import io.bisq.core.offer.Offer;
import io.bisq.network.p2p.storage.P2PService;
import io.bisq.protobuffer.message.offer.OfferAvailabilityResponse;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfferAvailabilityModel implements Model {
    private static final Logger log = LoggerFactory.getLogger(OfferAvailabilityModel.class);

    public final Offer offer;
    public final PubKeyRing pubKeyRing;
    public final P2PService p2PService;

    private NodeAddress peerNodeAddress;
    private OfferAvailabilityResponse message;

    public OfferAvailabilityModel(Offer offer,
                                  PubKeyRing pubKeyRing,
                                  P2PService p2PService) {
        this.offer = offer;
        this.pubKeyRing = pubKeyRing;
        this.p2PService = p2PService;
    }

    public NodeAddress getPeerNodeAddress() {
        return peerNodeAddress;
    }

    public void setPeerNodeAddress(NodeAddress peerNodeAddress) {
        this.peerNodeAddress = peerNodeAddress;
    }

    public void setMessage(OfferAvailabilityResponse message) {
        this.message = message;
    }

    public OfferAvailabilityResponse getMessage() {
        return message;
    }

    public long getTakersTradePrice() {
        return offer.getPrice() != null ? offer.getPrice().getValue() : 0;
    }

    @Override
    public void persist() {
    }

    @Override
    public void onComplete() {
    }
}

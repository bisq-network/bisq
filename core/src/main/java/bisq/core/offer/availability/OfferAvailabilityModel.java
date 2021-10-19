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

package bisq.core.offer.availability;

import bisq.core.offer.Offer;
import bisq.core.offer.availability.messages.OfferAvailabilityResponse;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.Model;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

public class OfferAvailabilityModel implements Model {
    @Getter
    private final Offer offer;
    @Getter
    private final PubKeyRing pubKeyRing; // takers PubKey (my pubkey)
    @Getter
    private final P2PService p2PService;
    @Getter
    final private User user;
    @Getter
    private final MediatorManager mediatorManager;
    @Getter
    private final TradeStatisticsManager tradeStatisticsManager;
    private NodeAddress peerNodeAddress;  // maker
    private OfferAvailabilityResponse message;
    @Nullable
    @Setter
    @Getter
    private NodeAddress selectedArbitrator;

    // Added in v1.1.6
    @Nullable
    @Setter
    @Getter
    private NodeAddress selectedMediator;

    // Added in v1.2.0
    @Nullable
    @Setter
    @Getter
    private NodeAddress selectedRefundAgent;

    // Added in v1.5.5
    @Getter
    private final boolean isTakerApiUser;

    public OfferAvailabilityModel(Offer offer,
                                  PubKeyRing pubKeyRing,
                                  P2PService p2PService,
                                  User user,
                                  MediatorManager mediatorManager,
                                  TradeStatisticsManager tradeStatisticsManager,
                                  boolean isTakerApiUser) {
        this.offer = offer;
        this.pubKeyRing = pubKeyRing;
        this.p2PService = p2PService;
        this.user = user;
        this.mediatorManager = mediatorManager;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.isTakerApiUser = isTakerApiUser;
    }

    public NodeAddress getPeerNodeAddress() {
        return peerNodeAddress;
    }

    void setPeerNodeAddress(NodeAddress peerNodeAddress) {
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
    public void onComplete() {
    }
}

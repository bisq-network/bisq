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

package io.bitsquare.trade.protocol.trade.taker;

import io.bitsquare.network.Peer;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.protocol.trade.offerer.messages.IsOfferAvailableResponseMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.trade.taker.tasks.RequestIsOfferAvailable;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for the correct execution of the sequence of tasks, message passing to the peer and message processing
 * from the peer.
 * That class handles the role of the taker as the Bitcoin seller.
 * It uses sub tasks to not pollute the main class too much with all the async result/fault handling.
 * Any data from incoming messages as well data used to send to the peer need to be validated before further processing.
 */
public class RequestIsOfferAvailableProtocol {
    private static final Logger log = LoggerFactory.getLogger(RequestIsOfferAvailableProtocol.class);


    // provided data
    private final Offer offer;
    private final TradeMessageService tradeMessageService;

    // derived
    private final String offerId;
    private final PublicKey offererMessagePublicKey;

    // written/read by task
    private Peer peer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestIsOfferAvailableProtocol(Offer offer,
                                           TradeMessageService tradeMessageService) {
        this.offer = offer;
        this.tradeMessageService = tradeMessageService;
        offerId = offer.getId();
        offererMessagePublicKey = offer.getMessagePublicKey();
    }

    public void start() {
        getPeerAddress();
    }

    // 1. GetPeerAddress
    // Async
    // In case of an error: Repeat once, then give up. 
    private void getPeerAddress() {
        log.debug("getPeerAddress called");
        GetPeerAddress.run(this::onResultGetPeerAddress, this::onGetPeerAddressFault, tradeMessageService, offererMessagePublicKey);
    }

    private void onGetPeerAddressFault(String errorMessage) {
        GetPeerAddress.run(this::onResultGetPeerAddress, this::handleErrorMessage, tradeMessageService, offererMessagePublicKey);
    }


    // 2. RequestTakeOffer
    // Async
    // In case of an error: Repeat once, then give up.
    public void onResultGetPeerAddress(Peer peer) {
        log.debug("onResultGetPeerAddress called");
        this.peer = peer;

        RequestIsOfferAvailable.run(this::onRequestIsOfferAvailableFault, peer, tradeMessageService, offerId);
    }

    private void onRequestIsOfferAvailableFault(String errorMessage) {
        RequestIsOfferAvailable.run(this::handleErrorMessage, peer, tradeMessageService, offerId);
    }

    // generic
    private void handleErrorMessage(String errorMessage) {
        offer.setState(Offer.State.OFFER_NOT_AVAILABLE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message from peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void handleIsOfferAvailableResponseMessage(IsOfferAvailableResponseMessage offerMessage) {
        if (offer.getState() != Offer.State.OFFER_REMOVED) {
            if (offerMessage.isOfferOpen())
                offer.setState(Offer.State.OFFER_AVAILABLE);
            else
                offer.setState(Offer.State.OFFER_NOT_AVAILABLE);
        }
    }
}

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

import io.bitsquare.network.Peer;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.protocol.trade.OfferMessage;
import io.bitsquare.util.handlers.ResultHandler;
import io.bitsquare.util.taskrunner.SharedModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckOfferAvailabilityModel extends SharedModel {
    private static final Logger log = LoggerFactory.getLogger(CheckOfferAvailabilityModel.class);

    private final Offer offer;
    private final TradeMessageService tradeMessageService;
    private final ResultHandler resultHandler;

    private Peer peer;
    private OfferMessage message;

    public CheckOfferAvailabilityModel(Offer offer, TradeMessageService tradeMessageService, ResultHandler resultHandler) {
        this.offer = offer;
        this.tradeMessageService = tradeMessageService;
        this.resultHandler = resultHandler;
    }

    // getter/setter
    public Offer getOffer() {
        return offer;
    }

    public TradeMessageService getTradeMessageService() {
        return tradeMessageService;
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

    public ResultHandler getResultHandler() {
        return resultHandler;
    }
}

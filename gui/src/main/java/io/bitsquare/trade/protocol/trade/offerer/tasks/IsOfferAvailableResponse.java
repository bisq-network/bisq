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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.network.Peer;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.trade.protocol.trade.offerer.messages.IsOfferAvailableResponseMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IsOfferAvailableResponse {
    private static final Logger log = LoggerFactory.getLogger(IsOfferAvailableResponse.class);

    public static void run(Peer peer,
                           TradeMessageService tradeMessageService,
                           String offerId,
                           boolean isOfferOpen) {
        log.trace("Run RespondToIsOfferAvailable task");
        IsOfferAvailableResponseMessage message = new IsOfferAvailableResponseMessage(offerId, isOfferOpen);
        tradeMessageService.sendMessage(peer, message, new SendMessageListener() {
            @Override
            public void handleResult() {
                log.trace("RespondToIsOfferAvailableMessage successfully arrived at peer");
                // Nothing to do. Taker knows now offer available state.
            }

            @Override
            public void handleFault() {
                log.error("RespondToIsOfferAvailableMessage did not arrive at peer");
                // Ignore that. Taker might have gone offline
            }
        });
    }
}
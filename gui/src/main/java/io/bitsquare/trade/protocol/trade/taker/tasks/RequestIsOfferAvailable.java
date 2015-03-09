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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.network.Peer;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestIsOfferAvailableMessage;
import io.bitsquare.util.handlers.ErrorMessageHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestIsOfferAvailable {
    private static final Logger log = LoggerFactory.getLogger(RequestIsOfferAvailable.class);

    public static void run(ErrorMessageHandler errorMessageHandler,
                           Peer peer, TradeMessageService tradeMessageService, String offerId) {
        log.trace("Run RequestIsOfferAvailable task");

        tradeMessageService.sendMessage(peer, new RequestIsOfferAvailableMessage(offerId),
                new SendMessageListener() {
                    @Override
                    public void handleResult() {
                        log.trace("RequestIsOfferAvailableMessage successfully arrived at peer");
                        // nothing to do
                    }

                    @Override
                    public void handleFault() {
                        log.error("RequestIsOfferAvailableMessage did not arrive at peer");
                        errorMessageHandler.handleErrorMessage("RequestIsOfferAvailableMessage did not arrive at peer");
                    }
                });
    }
}


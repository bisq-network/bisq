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
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.util.handlers.ErrorMessageHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RespondToTakeOfferRequest {
    private static final Logger log = LoggerFactory.getLogger(RespondToTakeOfferRequest.class);

    public static void run(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler,
                           TradeMessageService tradeMessageService, Peer peer, Trade.State tradeState, String tradeId) {
        log.trace("Run HandleTakeOfferRequest task");
        boolean takeOfferRequestAccepted = tradeState == Trade.State.OPEN;
        if (!takeOfferRequestAccepted) {
            log.warn("Received take offer request but the offer not marked as open anymore.");
        }
        RespondToTakeOfferRequestMessage tradeMessage = new RespondToTakeOfferRequestMessage(tradeId, takeOfferRequestAccepted);
        tradeMessageService.sendMessage(peer, tradeMessage, new SendMessageListener() {
            @Override
            public void handleResult() {
                log.trace("RespondToTakeOfferRequestMessage successfully arrived at peer");
                resultHandler.handleResult();
            }

            @Override
            public void handleFault() {
                log.error("AcceptTakeOfferRequestMessage  did not arrive at peer");
                errorMessageHandler.handleErrorMessage("AcceptTakeOfferRequestMessage did not arrive at peer");
            }
        });
    }

    public interface ResultHandler {
        void handleResult();
    }
}

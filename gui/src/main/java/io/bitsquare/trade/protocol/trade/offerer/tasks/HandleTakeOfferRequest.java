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

import io.bitsquare.msg.MessageService;
import io.bitsquare.msg.listeners.OutgoingMessageListener;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.util.handlers.ExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleTakeOfferRequest {
    private static final Logger log = LoggerFactory.getLogger(HandleTakeOfferRequest.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, Peer peer,
                           MessageService messageService, Trade.State tradeState, String tradeId) {
        log.trace("Run task");
        boolean isTradeIsOpen = tradeState == Trade.State.OPEN;
        if (!isTradeIsOpen) {
            log.warn("Received take offer request but the offer not marked as open anymore.");
        }
        RespondToTakeOfferRequestMessage tradeMessage =
                new RespondToTakeOfferRequestMessage(tradeId, isTradeIsOpen);
        messageService.sendMessage(peer, tradeMessage, new OutgoingMessageListener() {
            @Override
            public void onResult() {
                log.trace("RespondToTakeOfferRequestMessage successfully arrived at peer");
                resultHandler.onResult(isTradeIsOpen);
            }

            @Override
            public void onFailed() {
                log.error("AcceptTakeOfferRequestMessage  did not arrive at peer");
                exceptionHandler.handleException(new Exception("AcceptTakeOfferRequestMessage did not arrive at peer"));
            }
        });
    }

    public interface ResultHandler {
        void onResult(boolean takeOfferRequestAccepted);
    }
}

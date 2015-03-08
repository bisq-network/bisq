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

import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.listeners.OutgoingMessageListener;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestTakeOfferMessage;
import io.bitsquare.util.handlers.ExceptionHandler;
import io.bitsquare.util.handlers.ResultHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakeOffer {
    private static final Logger log = LoggerFactory.getLogger(RequestTakeOffer.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, Peer peer,
                           TradeMessageService tradeMessageService, String tradeId) {
        log.trace("Run task");
        tradeMessageService.sendMessage(peer, new RequestTakeOfferMessage(tradeId),
                new OutgoingMessageListener() {
                    @Override
                    public void onResult() {
                        log.trace("RequestTakeOfferMessage successfully arrived at peer");
                        resultHandler.handleResult();
                    }

                    @Override
                    public void onFailed() {
                        log.error("RequestTakeOfferMessage  did not arrive at peer");
                        exceptionHandler.handleException(new Exception("RequestTakeOfferMessage did not arrive at " +
                                "peer"));
                    }
                });
    }
}

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
import io.bitsquare.trade.protocol.trade.taker.messages.TakeOfferFeePayedMessage;
import io.bitsquare.util.handlers.ErrorMessageHandler;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendTakeOfferFeePayedMessage {
    private static final Logger log = LoggerFactory.getLogger(SendTakeOfferFeePayedMessage.class);

    public static void run(ErrorMessageHandler errorMessageHandler,
                           Peer peer,
                           TradeMessageService tradeMessageService,
                           String tradeId,
                           String takeOfferFeeTxId,
                           Coin tradeAmount,
                           String tradePubKeyAsHex) {
        log.trace("Run SendTakeOfferFeePayedMessage task");
        TakeOfferFeePayedMessage msg = new TakeOfferFeePayedMessage(tradeId, takeOfferFeeTxId, tradeAmount,
                tradePubKeyAsHex);

        tradeMessageService.sendMessage(peer, msg, new SendMessageListener() {
            @Override
            public void handleResult() {
                log.trace("TakeOfferFeePayedMessage succeeded.");
            }

            @Override
            public void handleFault() {
                log.error("Sending TakeOfferFeePayedMessage failed.");
                errorMessageHandler.handleErrorMessage("Sending TakeOfferFeePayedMessage failed.");
            }
        });
    }
}

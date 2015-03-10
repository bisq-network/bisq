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
import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.util.handlers.ErrorMessageHandler;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDepositTxIdToTaker {
    private static final Logger log = LoggerFactory.getLogger(SendDepositTxIdToTaker.class);

    public static void run( ErrorMessageHandler errorMessageHandler, Peer peer,
                           TradeMessageService tradeMessageService, String tradeId, Transaction depositTransaction) {
        log.trace("Run task");
        DepositTxPublishedMessage tradeMessage =
                new DepositTxPublishedMessage(tradeId, Utils.HEX.encode(depositTransaction.bitcoinSerialize()));

        tradeMessageService.sendMessage(peer, tradeMessage, new SendMessageListener() {
            @Override
            public void handleResult() {
                log.trace("DepositTxPublishedMessage successfully arrived at peer");
            }

            @Override
            public void handleFault() {
                log.error("DepositTxPublishedMessage  did not arrive at peer");
                errorMessageHandler.handleErrorMessage("DepositTxPublishedMessage did not arrive at peer");
            }
        });
    }

}

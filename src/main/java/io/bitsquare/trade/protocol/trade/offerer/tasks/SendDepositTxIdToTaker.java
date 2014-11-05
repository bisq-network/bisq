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

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingMessageListener;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.util.task.ExceptionHandler;
import io.bitsquare.util.task.ResultHandler;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDepositTxIdToTaker {
    private static final Logger log = LoggerFactory.getLogger(SendDepositTxIdToTaker.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, Peer peer,
                           MessageFacade messageFacade, String tradeId, Transaction depositTransaction) {
        log.trace("Run task");
        DepositTxPublishedMessage tradeMessage =
                new DepositTxPublishedMessage(tradeId, Utils.HEX.encode(depositTransaction.bitcoinSerialize()));

        messageFacade.sendMessage(peer, tradeMessage, new OutgoingMessageListener() {
            @Override
            public void onResult() {
                log.trace("DepositTxPublishedMessage successfully arrived at peer");
                resultHandler.handleResult();
            }

            @Override
            public void onFailed() {
                log.error("DepositTxPublishedMessage  did not arrive at peer");
                exceptionHandler.handleException(new Exception("DepositTxPublishedMessage did not arrive at peer"));
            }
        });
    }

}

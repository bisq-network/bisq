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

import io.bitsquare.bank.BankAccount;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingMessageListener;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.protocol.trade.offerer.messages.RequestTakerDepositPaymentMessage;
import io.bitsquare.util.task.ExceptionHandler;
import io.bitsquare.util.task.ResultHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakerDepositPayment {
    private static final Logger log = LoggerFactory.getLogger(RequestTakerDepositPayment.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           Peer peer,
                           MessageFacade messageFacade,
                           String tradeId,
                           BankAccount bankAccount,
                           String accountId,
                           String offererPubKey,
                           String preparedOffererDepositTxAsHex,
                           long offererTxOutIndex) {
        log.trace("Run task");
        RequestTakerDepositPaymentMessage tradeMessage = new RequestTakerDepositPaymentMessage(
                tradeId, bankAccount, accountId, offererPubKey, preparedOffererDepositTxAsHex, offererTxOutIndex);
        messageFacade.sendMessage(peer, tradeMessage, new OutgoingMessageListener() {
            @Override
            public void onResult() {
                log.trace("RequestTakerDepositPaymentMessage successfully arrived at peer");
                resultHandler.handleResult();
            }

            @Override
            public void onFailed() {
                log.error("RequestTakerDepositPaymentMessage  did not arrive at peer");
                exceptionHandler.handleException(new Exception("RequestTakerDepositPaymentMessage did not arrive at " +
                        "peer"));
            }
        });
    }

}

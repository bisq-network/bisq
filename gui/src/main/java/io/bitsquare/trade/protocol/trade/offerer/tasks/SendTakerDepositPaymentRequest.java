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
import io.bitsquare.network.Peer;
import io.bitsquare.trade.TradeMessageService;
import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.trade.protocol.trade.offerer.messages.TakerDepositPaymentRequestMessage;
import io.bitsquare.util.handlers.ErrorMessageHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendTakerDepositPaymentRequest {
    private static final Logger log = LoggerFactory.getLogger(SendTakerDepositPaymentRequest.class);

    public static void run(ErrorMessageHandler errorMessageHandler,
                           Peer peer,
                           TradeMessageService tradeMessageService,
                           String tradeId,
                           BankAccount bankAccount,
                           String accountId,
                           String offererPubKey,
                           String preparedOffererDepositTxAsHex,
                           long offererTxOutIndex) {
        log.trace("Run SendTakerDepositPaymentRequest task");
        TakerDepositPaymentRequestMessage tradeMessage = new TakerDepositPaymentRequestMessage(
                tradeId, bankAccount, accountId, offererPubKey, preparedOffererDepositTxAsHex, offererTxOutIndex);
        tradeMessageService.sendMessage(peer, tradeMessage, new SendMessageListener() {
            @Override
            public void handleResult() {
                log.trace("RequestTakerDepositPaymentMessage successfully arrived at peer");
            }

            @Override
            public void handleFault() {
                log.error("RequestTakerDepositPaymentMessage  did not arrive at peer");
                errorMessageHandler.handleErrorMessage("RequestTakerDepositPaymentMessage did not arrive at peer");
            }
        });
    }

}

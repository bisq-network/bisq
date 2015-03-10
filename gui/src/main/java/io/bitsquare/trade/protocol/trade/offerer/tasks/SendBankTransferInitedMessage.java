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
import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferInitedMessage;
import io.bitsquare.util.handlers.ExceptionHandler;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendBankTransferInitedMessage {
    private static final Logger log = LoggerFactory.getLogger(SendBankTransferInitedMessage.class);

    public static void run(ExceptionHandler exceptionHandler,
                           Peer peer,
                           TradeMessageService tradeMessageService,
                           String tradeId,
                           String depositTxAsHex,
                           String offererSignatureR,
                           String offererSignatureS,
                           Coin offererPaybackAmount,
                           Coin takerPaybackAmount,
                           String offererPayoutAddress) {
        log.trace("Run SendSignedPayoutTx task");
        try {
            BankTransferInitedMessage tradeMessage = new BankTransferInitedMessage(tradeId,
                    depositTxAsHex,
                    offererSignatureR,
                    offererSignatureS,
                    offererPaybackAmount,
                    takerPaybackAmount,
                    offererPayoutAddress);
            tradeMessageService.sendMessage(peer, tradeMessage, new SendMessageListener() {
                @Override
                public void handleResult() {
                    log.trace("Sending BankTransferInitedMessage succeeded.");
                }

                @Override
                public void handleFault() {
                    exceptionHandler.handleException(new Exception("Sending BankTransferInitedMessage failed."));

                }
            });
        } catch (Exception e) {
            exceptionHandler.handleException(e);
        }
    }
}

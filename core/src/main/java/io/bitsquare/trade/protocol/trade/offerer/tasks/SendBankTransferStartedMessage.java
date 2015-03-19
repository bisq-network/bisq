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

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.network.listener.SendMessageListener;
import io.bitsquare.trade.protocol.trade.messages.BankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.offerer.models.BuyerAsOffererModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendBankTransferStartedMessage extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(SendBankTransferStartedMessage.class);

    public SendBankTransferStartedMessage(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        BankTransferStartedMessage tradeMessage = new BankTransferStartedMessage(
                model.id,
                model.offerer.payoutTxSignature,
                model.offerer.payoutAmount,
                model.taker.payoutAmount,
                model.offerer.addressEntry.getAddressString());
        model.messageService.sendMessage(model.taker.peer, tradeMessage, new SendMessageListener() {
            @Override
            public void handleResult() {
                log.trace("Sending BankTransferInitedMessage succeeded.");
                complete();
            }

            @Override
            public void handleFault() {
                failed("Sending BankTransferInitedMessage failed.");
            }
        });
    }

    @Override
    protected void updateStateOnFault() {
    }
}

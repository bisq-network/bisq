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

import io.bitsquare.trade.Trade;
import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.trade.protocol.trade.taker.models.SellerAsTakerModel;
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestDepositTxInputsMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendRequestDepositTxInputsMessage extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(SendRequestDepositTxInputsMessage.class);

    public SendRequestDepositTxInputsMessage(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    private int retryCounter = 0;

    @Override
    protected void doRun() {
        RequestDepositTxInputsMessage msg = new RequestDepositTxInputsMessage(
                model.getId(),
                model.getTakeOfferFeeTx().getHashAsString(),
                model.getTrade().getTradeAmount(),
                model.taker.pubKey
        );

        model.getTradeMessageService().sendMessage(model.offerer.peer, msg, new SendMessageListener() {
            @Override
            public void handleResult() {
                log.trace("Sending TakeOfferFeePayedMessage succeeded.");
                complete();
            }

            @Override
            public void handleFault() {
                // Take offer fee is already paid, so we need to try to get that trade to succeed.
                // We try to repeat once and if that fails as well we persist the state for a later retry.
                if (retryCounter == 0) {
                    retryCounter++;
                   // doRun();
                }
                else {
                    failed();
                }
            }
        });
    }

    @Override
    protected void updateStateOnFault() {
        appendToErrorMessage("Sending TakeOfferFeePayedMessage to offerer failed. Maybe the network connection was lost or the offerer lost his connection. " +
                "We persisted the state of the trade, please try again later or cancel that trade.");
        model.getTrade().setState(Trade.State.MESSAGE_SENDING_FAILED);
    }
}
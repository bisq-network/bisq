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

import io.bitsquare.offer.OpenOffer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererModel;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.util.tasks.Task;
import io.bitsquare.util.tasks.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RespondToTakeOfferRequest extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(RespondToTakeOfferRequest.class);

    public RespondToTakeOfferRequest(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        boolean takeOfferRequestAccepted = model.getOpenOffer().getState() == OpenOffer.State.OPEN;
        if (!takeOfferRequestAccepted)
            log.info("Received take offer request but the offer not marked as open anymore.");

        Trade trade = new Trade(model.getOpenOffer().getOffer());
        model.setTrade(trade);
        model.getOpenOffer().setState(OpenOffer.State.OFFER_ACCEPTED);

        RespondToTakeOfferRequestMessage tradeMessage = new RespondToTakeOfferRequestMessage(trade.getId(), takeOfferRequestAccepted);
        model.getTradeMessageService().sendMessage(model.getPeer(), tradeMessage, new SendMessageListener() {
            @Override
            public void handleResult() {
                log.trace("RespondToTakeOfferRequestMessage successfully arrived at peer");
                complete();
            }

            @Override
            public void handleFault() {
                failed("AcceptTakeOfferRequestMessage did not arrive at peer");
            }
        });

    }
}

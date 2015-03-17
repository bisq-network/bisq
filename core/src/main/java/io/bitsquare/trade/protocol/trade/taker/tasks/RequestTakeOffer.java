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

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestTakeOfferMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakeOffer extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(RequestTakeOffer.class);

    public RequestTakeOffer(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            model.getTradeMessageService().sendMessage(model.getOfferer(), new RequestTakeOfferMessage(model.getId()),
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            complete();
                        }

                        @Override
                        public void handleFault() {
                            model.getOffer().setState(Offer.State.OFFERER_OFFLINE);
                            
                            failed();
                        }
                    });
        } catch (Throwable t) {
            model.getOffer().setState(Offer.State.FAULT);

            failed(t);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}

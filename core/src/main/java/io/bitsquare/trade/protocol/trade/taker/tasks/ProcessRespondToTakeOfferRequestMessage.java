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
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.checkTradeId;

public class ProcessRespondToTakeOfferRequestMessage extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessRespondToTakeOfferRequestMessage.class);

    public ProcessRespondToTakeOfferRequestMessage(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(model.getTrade().getId(), model.getTradeMessage());

            if (((RespondToTakeOfferRequestMessage) model.getTradeMessage()).isOfferIsAvailable()) {
                model.getTrade().setState(Trade.State.OFFERER_ACCEPTED);
                complete();
            }
            else {
                model.getTrade().setState(Trade.State.OFFERER_REJECTED);
                failed("Requested offer rejected because it is not available anymore.");
            }
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected void applyStateOnFault() {
        // do nothing
    }
}
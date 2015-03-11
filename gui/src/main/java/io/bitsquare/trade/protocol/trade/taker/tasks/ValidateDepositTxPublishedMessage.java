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

import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.SellerTakesOfferModel;
import io.bitsquare.util.tasks.Task;
import io.bitsquare.util.tasks.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkState;
import static io.bitsquare.util.Validator.*;

public class ValidateDepositTxPublishedMessage extends Task<SellerTakesOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(ValidateDepositTxPublishedMessage.class);

    public ValidateDepositTxPublishedMessage(TaskRunner taskHandler, SellerTakesOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            checkState(model.getTrade().getPreviousTask() == SendSignedTakerDepositTxAsHex.class);
            checkTradeId(model.getTradeId(), model.getTradeMessage());
            
            DepositTxPublishedMessage message = (DepositTxPublishedMessage) model.getTradeMessage();
            model.setDepositTxAsHex(nonEmptyStringOf(message.getDepositTxAsHex()));

            complete();
        } catch (Throwable t) {
            failed("Validation for DepositTxPublishedMessage failed.", t);
        }
    }
}
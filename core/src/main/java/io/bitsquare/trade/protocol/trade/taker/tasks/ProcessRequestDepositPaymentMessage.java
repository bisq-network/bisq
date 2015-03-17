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
import io.bitsquare.trade.protocol.trade.offerer.messages.RequestDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.taker.models.SellerAsTakerModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

public class ProcessRequestDepositPaymentMessage extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessRequestDepositPaymentMessage.class);

    public ProcessRequestDepositPaymentMessage(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(model.getId(), model.getTradeMessage());
            RequestDepositPaymentMessage message = (RequestDepositPaymentMessage) model.getTradeMessage();

            model.offerer.connectedOutputsForAllInputs = checkNotNull(message.getOffererConnectedOutputsForAllInputs());
            checkArgument(message.getOffererConnectedOutputsForAllInputs().size() > 0);
            model.offerer.outputs = checkNotNull(message.getOffererOutputs());
            model.offerer.pubKey = checkNotNull(message.getOffererPubKey());
            model.taker.fiatAccount = checkNotNull(message.getFiatAccount());
            model.taker.accountId = nonEmptyStringOf(message.getAccountId());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}
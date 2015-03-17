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
import io.bitsquare.trade.protocol.trade.offerer.models.BuyerAsOffererModel;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestOffererPublishDepositTxMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

public class ProcessRequestOffererPublishDepositTxMessage extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessRequestOffererPublishDepositTxMessage.class);

    public ProcessRequestOffererPublishDepositTxMessage(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(model.getId(), model.getTradeMessage());
            RequestOffererPublishDepositTxMessage message = (RequestOffererPublishDepositTxMessage) model.getTradeMessage();

            model.taker.fiatAccount = checkNotNull(message.getTakerBankAccount());
            model.taker.accountId = nonEmptyStringOf(message.getTakerAccountId());
            model.taker.messagePublicKey = checkNotNull(message.getTakerMessagePublicKey());
            model.taker.contractAsJson = nonEmptyStringOf(message.getTakerContractAsJson());
            model.taker.payoutAddress = nonEmptyStringOf(message.getTakerPayoutAddress());
            model.taker.depositTx = checkNotNull(message.getTakersDepositTx());
            model.taker.connectedOutputsForAllInputs = checkNotNull(message.getTakerConnectedOutputsForAllInputs());
            checkArgument(message.getTakerConnectedOutputsForAllInputs().size() > 0);
            model.taker.outputs = checkNotNull(message.getTakerOutputs());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}
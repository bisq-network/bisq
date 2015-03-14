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

import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererModel;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class ProcessRequestOffererPublishDepositTxMessage extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessRequestOffererPublishDepositTxMessage.class);

    public ProcessRequestOffererPublishDepositTxMessage(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(model.getTrade().getId(), model.getTradeMessage());

            RequestOffererPublishDepositTxMessage message = (RequestOffererPublishDepositTxMessage) model.getTradeMessage();
            model.setTakerPayoutAddress(nonEmptyStringOf(message.getTakerPayoutAddress()));
            model.setPeersAccountId(nonEmptyStringOf(message.getTakerAccountId()));
            model.setPeersBankAccount(checkNotNull(message.getTakerBankAccount()));
            model.setPeersMessagePublicKey(checkNotNull(message.getTakerMessagePublicKey()));
            model.setPeersContractAsJson(nonEmptyStringOf(message.getTakerContractAsJson()));
            model.setSignedTakerDepositTxAsHex(nonEmptyStringOf(message.getSignedTakerDepositTxAsHex()));
            model.setTxConnOutAsHex(nonEmptyStringOf(message.getTxConnOutAsHex()));
            model.setTxScriptSigAsHex(nonEmptyStringOf(message.getTxScriptSigAsHex()));
            model.setTakerTxOutIndex(nonNegativeLongOf(message.getTakerTxOutIndex()));

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    } @Override
      protected void rollBackOnFault() {
    }
}
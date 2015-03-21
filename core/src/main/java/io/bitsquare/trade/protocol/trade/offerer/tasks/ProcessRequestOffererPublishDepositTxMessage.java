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
import io.bitsquare.trade.protocol.trade.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.offerer.models.BuyerAsOffererModel;

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
            checkTradeId(model.id, model.getTradeMessage());
            RequestOffererPublishDepositTxMessage message = (RequestOffererPublishDepositTxMessage) model.getTradeMessage();

            model.taker.fiatAccount = checkNotNull(message.takerFiatAccount);
            model.taker.accountId = nonEmptyStringOf(message.takerAccountId);
            model.taker.p2pSigPublicKey = checkNotNull(message.takerP2PSigPublicKey);
            model.taker.p2pEncryptPubKey = checkNotNull(message.takerP2PEncryptPublicKey);
            model.taker.contractAsJson = nonEmptyStringOf(message.takerContractAsJson);
            model.taker.contractSignature = nonEmptyStringOf(message.takerContractSignature);
            model.taker.payoutAddressString = nonEmptyStringOf(message.takerPayoutAddressString);
            model.taker.preparedDepositTx = checkNotNull(message.takersPreparedDepositTx);
            model.taker.connectedOutputsForAllInputs = checkNotNull(message.takerConnectedOutputsForAllInputs);
            checkArgument(message.takerConnectedOutputsForAllInputs.size() > 0);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
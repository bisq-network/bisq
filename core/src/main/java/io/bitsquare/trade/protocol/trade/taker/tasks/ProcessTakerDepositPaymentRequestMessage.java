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

import io.bitsquare.trade.protocol.trade.offerer.messages.TakerDepositPaymentRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class ProcessTakerDepositPaymentRequestMessage extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessTakerDepositPaymentRequestMessage.class);

    public ProcessTakerDepositPaymentRequestMessage(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(model.getTrade().getId(), model.getTradeMessage());
            TakerDepositPaymentRequestMessage message = (TakerDepositPaymentRequestMessage) model.getTradeMessage();
            model.setPeersAccountId(nonEmptyStringOf(message.getAccountId()));
            model.setPeersBankAccount(checkNotNull(message.getBankAccount()));
            model.setPeersPubKey(nonEmptyStringOf(message.getOffererPubKey()));
            model.setPreparedPeersDepositTxAsHex(nonEmptyStringOf(message.getPreparedOffererDepositTxAsHex()));
            model.setPeersTxOutIndex(nonNegativeLongOf(message.getOffererTxOutIndex()));

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected void applyStateOnFault() {
    }
}
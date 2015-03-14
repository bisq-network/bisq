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

import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererModel;
import io.bitsquare.trade.protocol.trade.taker.messages.PayoutTxPublishedMessage;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.*;

public class ProcessPayoutTxPublishedMessage extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessPayoutTxPublishedMessage.class);

    public ProcessPayoutTxPublishedMessage(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(model.getTrade().getId(), model.getTradeMessage());
            String payoutTxAsHex = nonEmptyStringOf(((PayoutTxPublishedMessage) model.getTradeMessage()).getPayoutTxAsHex());
            Transaction payoutTx = new Transaction(model.getWalletService().getWallet().getParams(), Utils.parseAsHexOrBase58(payoutTxAsHex));

            model.getTrade().setPayoutTx(payoutTx);
            model.getTrade().setState(Trade.State.PAYOUT_PUBLISHED);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected void rollBackOnFault() {
    }
}
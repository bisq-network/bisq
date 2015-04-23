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

package io.bitsquare.trade.protocol.trade.tasks.seller;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeState;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.checkTradeId;

public class ProcessDepositTxPublishedMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessDepositTxPublishedMessage.class);

    public ProcessDepositTxPublishedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            DepositTxPublishedMessage message = (DepositTxPublishedMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            trade.setDepositTx(checkNotNull(message.depositTx));

            trade.setTradeState(TradeState.SellerState.DEPOSIT_PUBLISHED_MSG_RECEIVED);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();

            failed(t);
        }
    }
}
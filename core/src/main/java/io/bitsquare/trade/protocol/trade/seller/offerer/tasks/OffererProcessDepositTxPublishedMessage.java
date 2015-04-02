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

package io.bitsquare.trade.protocol.trade.seller.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.checkTradeId;

public class OffererProcessDepositTxPublishedMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererProcessDepositTxPublishedMessage.class);

    public OffererProcessDepositTxPublishedMessage(TaskRunner taskHandler, Trade offererTrade) {
        super(taskHandler, offererTrade);
    }

    @Override
    protected void doRun() {
        try {
            DepositTxPublishedMessage message = (DepositTxPublishedMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            offererTrade.setDepositTx(checkNotNull(message.depositTx));

            if (offererTrade instanceof OffererAsBuyerTrade)
                offererTrade.setProcessState(OffererAsBuyerTrade.ProcessState.DEPOSIT_PUBLISHED);
            else if (offererTrade instanceof OffererAsSellerTrade)
                offererTrade.setProcessState(OffererAsSellerTrade.ProcessState.DEPOSIT_PUBLISHED);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            failed(t);
        }
    }
}
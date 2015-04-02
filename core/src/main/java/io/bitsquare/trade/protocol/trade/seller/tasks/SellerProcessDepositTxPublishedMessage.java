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

package io.bitsquare.trade.protocol.trade.seller.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.BuyerAsTakerTrade;
import io.bitsquare.trade.SellerAsOffererTrade;
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeTask;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.states.OffererState;
import io.bitsquare.trade.states.TakerState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.checkTradeId;

public class SellerProcessDepositTxPublishedMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SellerProcessDepositTxPublishedMessage.class);

    public SellerProcessDepositTxPublishedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            DepositTxPublishedMessage message = (DepositTxPublishedMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            trade.setDepositTx(checkNotNull(message.depositTx));

            if (trade instanceof BuyerAsOffererTrade || trade instanceof SellerAsOffererTrade)
                trade.setProcessState(OffererState.ProcessState.DEPOSIT_PUBLISHED);
            else if (trade instanceof BuyerAsTakerTrade || trade instanceof SellerAsTakerTrade)
                trade.setProcessState(TakerState.ProcessState.DEPOSIT_PUBLISHED);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}
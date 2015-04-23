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

package io.bitsquare.trade.protocol.trade.tasks.shared;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeState;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;

import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitPayoutTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(CommitPayoutTx.class);

    public CommitPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Transaction transaction = processModel.getTradeWalletService().commitTx(trade.getPayoutTx());

            trade.setPayoutTx(transaction);

            if (trade instanceof BuyerTrade)
                trade.setTradeState(TradeState.BuyerState.PAYOUT_TX_COMMITTED);
            else if (trade instanceof SellerTrade)
                trade.setTradeState(TradeState.SellerState.PAYOUT_TX_COMMITTED);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();

            failed(t);
        }
    }
}
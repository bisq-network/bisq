/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.statistics.TradeStatistics2;

public class PublishTradeStatistics extends TradeTask {
    public PublishTradeStatistics(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (trade.getDepositTx() != null) {
                TradeStatistics2 tradeStatistics = new TradeStatistics2(trade.getOffer().getOfferPayload(),
                        trade.getTradePrice(),
                        trade.getTradeAmount(),
                        trade.getDate(),
                        trade.getDepositTx().getHashAsString());
                processModel.getP2PService().addPersistableNetworkPayload(tradeStatistics, true);
            }
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

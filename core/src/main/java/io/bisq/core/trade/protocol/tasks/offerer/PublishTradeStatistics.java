/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.offerer;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.protobuffer.payload.trade.statistics.TradeStatistics;

public class PublishTradeStatistics extends TradeTask {
    public PublishTradeStatistics(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            // Offerer is responsible for publishing. Only in case the offerer uses an old version the taker publishes.
            TradeStatistics tradeStatistics = new TradeStatistics(trade.getOffer().getOfferPayload(),
                    trade.getTradePrice(),
                    trade.getTradeAmount(),
                    trade.getDate(),
                    (trade.getDepositTx() != null ? trade.getDepositTx().getHashAsString() : ""),
                    processModel.getPubKeyRing());
            processModel.getP2PService().addData(tradeStatistics, true);
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

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

package io.bisq.trade.protocol.trade.tasks.taker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.trade.Trade;
import io.bisq.trade.protocol.trade.ArbitrationSelectionRule;
import io.bisq.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectArbitrator extends TradeTask {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(SelectArbitrator.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SelectArbitrator(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            trade.applyArbitratorNodeAddress(ArbitrationSelectionRule.select(processModel.getUser().getAcceptedArbitratorAddresses(), processModel.getOffer().getOfferPayload()));

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

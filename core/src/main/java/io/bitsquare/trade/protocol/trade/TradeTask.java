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

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.shared.models.ProcessModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeTask extends Task<Trade> {
    private static final Logger log = LoggerFactory.getLogger(TradeTask.class);

    protected final ProcessModel processModel;
    protected final Trade trade;

    public TradeTask(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);

        this.trade = trade;
        processModel = trade.getProcessModel();
    }

    @Override
    protected void doRun() {
    }
}

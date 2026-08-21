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

package bisq.core.trade.protocol.bsq_swap.tasks;

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BsqSwapTask extends Task<TradeModel> {
    protected final BsqSwapProtocolModel protocolModel;
    protected final BsqSwapTrade trade;

    protected BsqSwapTask(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);

        this.trade = bsqSwapTrade;
        protocolModel = bsqSwapTrade.getBsqSwapProtocolModel();
    }

    @Override
    protected void complete() {
        protocolModel.getTradeManager().requestPersistence();

        super.complete();
    }

    @Override
    protected void failed() {
        trade.setErrorMessage(errorMessage);
        protocolModel.getTradeManager().requestPersistence();

        super.failed();
    }

    @Override
    protected void failed(String message) {
        appendToErrorMessage(message);
        trade.setErrorMessage(errorMessage);
        protocolModel.getTradeManager().requestPersistence();

        super.failed();
    }

    @Override
    protected void failed(Throwable t) {
        t.printStackTrace();
        appendExceptionToErrorMessage(t);
        trade.setErrorMessage(errorMessage);
        protocolModel.getTradeManager().requestPersistence();

        super.failed();
    }
}

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

package bisq.core.trade.protocol.tasks;

import bisq.core.trade.atomic.protocol.BsqSwapProtocolModel;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AtomicTradeTask extends Task<TradeModel> {
    protected final BsqSwapProtocolModel bsqSwapProtocolModel;
    protected final BsqSwapTrade bsqSwapTrade;

    protected AtomicTradeTask(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);

        this.bsqSwapTrade = bsqSwapTrade;
        bsqSwapProtocolModel = bsqSwapTrade.getBsqSwapProtocolModel();
    }

    @Override
    protected void complete() {
        bsqSwapProtocolModel.getTradeManager().requestPersistence();

        super.complete();
    }

    @Override
    protected void failed() {
        bsqSwapTrade.setErrorMessage(errorMessage);
        bsqSwapProtocolModel.getTradeManager().requestPersistence();

        super.failed();
    }

    @Override
    protected void failed(String message) {
        appendToErrorMessage(message);
        bsqSwapTrade.setErrorMessage(errorMessage);
        bsqSwapProtocolModel.getTradeManager().requestPersistence();

        super.failed();
    }

    @Override
    protected void failed(Throwable t) {
        t.printStackTrace();
        appendExceptionToErrorMessage(t);
        bsqSwapTrade.setErrorMessage(errorMessage);
        bsqSwapProtocolModel.getTradeManager().requestPersistence();

        super.failed();
    }
}

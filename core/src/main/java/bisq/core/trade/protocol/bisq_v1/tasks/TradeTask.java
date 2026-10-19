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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TradeTask extends Task<TradeModel> {
    protected final ProcessModel processModel;
    protected final Trade trade;
    private final boolean tradeErrorOnFailure;

    protected TradeTask(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);

        this.trade = trade;
        processModel = trade.getProcessModel();
        // instanceof TradeTaskRunner does not compile on a TaskRunner<Trade>, as TradeTaskRunner is a
        // TaskRunner<TradeModel>
        tradeErrorOnFailure = !((TaskRunner<?>) taskHandler instanceof TradeTaskRunner tradeTaskRunner) ||
                tradeTaskRunner.isTradeErrorOnFailure();
    }

    @Override
    protected void complete() {
        processModel.getTradeManager().requestPersistence();

        super.complete();
    }

    @Override
    protected void failed() {
        setTradeErrorMessage();
        processModel.getTradeManager().requestPersistence();

        super.failed();
    }

    @Override
    protected void failed(String message) {
        appendToErrorMessage(message);
        setTradeErrorMessage();
        processModel.getTradeManager().requestPersistence();

        super.failed();
    }

    @Override
    protected void failed(Throwable t) {
        t.printStackTrace();
        appendExceptionToErrorMessage(t);
        setTradeErrorMessage();
        processModel.getTradeManager().requestPersistence();

        super.failed();
    }

    private void setTradeErrorMessage() {
        if (tradeErrorOnFailure) {
            trade.setErrorMessage(errorMessage);
        } else {
            log.warn("{} failed in an attempt that the trader did not start. tradeId={}, errorMessage={}",
                    getClass().getSimpleName(), trade.getId(), errorMessage);
        }
    }
}

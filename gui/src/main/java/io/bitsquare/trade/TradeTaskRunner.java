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

package io.bitsquare.trade;

import io.bitsquare.trade.protocol.trade.TradeSharedModel;
import io.bitsquare.util.handlers.FaultHandler;
import io.bitsquare.util.handlers.ResultHandler;
import io.bitsquare.util.tasks.Task;
import io.bitsquare.util.tasks.TaskRunner;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeTaskRunner<T extends TradeSharedModel> extends TaskRunner<TradeSharedModel> {
    private static final Logger log = LoggerFactory.getLogger(TradeTaskRunner.class);

    public TradeTaskRunner(T sharedModel, ResultHandler resultHandler, FaultHandler faultHandler) {
        super(sharedModel, resultHandler, faultHandler);
    }

    @Override
    protected void setCurrentTask(Class<? extends Task> task) {
        super.setCurrentTask(task);
        sharedModel.getTrade().setCurrentTask(task);
    }

    @Override
    protected void setPreviousTask(Class<? extends Task> task) {
        super.setPreviousTask(task);
        if (task != null)
            sharedModel.getTrade().setPreviousTask(task);
    }

    @Override
    public void handleFault(String message, @NotNull Throwable throwable) {
        sharedModel.getTrade().setState(Trade.State.FAILED);
        super.handleFault(message, throwable);
    }
}

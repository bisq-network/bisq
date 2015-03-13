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

package io.bitsquare.trade.protocol.trade.offerer;

import io.bitsquare.util.handlers.ErrorMessageHandler;
import io.bitsquare.util.handlers.ResultHandler;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerAsOffererTaskRunner<T extends BuyerAsOffererModel> extends TaskRunner<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererTaskRunner.class);

    public BuyerAsOffererTaskRunner(T sharedModel, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        super(sharedModel, resultHandler, errorMessageHandler);
    }

  /*  @Override
    public void handleFault(String message, @NotNull Throwable throwable) {
        sharedModel.getTrade().setState(Trade.State.FAILED);
        super.handleFault(message, throwable);
    }*/
}

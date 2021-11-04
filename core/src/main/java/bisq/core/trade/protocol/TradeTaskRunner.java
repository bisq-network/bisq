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

package bisq.core.trade.protocol;

import bisq.core.trade.model.TradeModel;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.taskrunner.TaskRunner;

public class TradeTaskRunner extends TaskRunner<TradeModel> {

    public TradeTaskRunner(TradeModel sharedModel,
                           ResultHandler resultHandler,
                           ErrorMessageHandler errorMessageHandler) {
        super(sharedModel, getSharedModelClass(sharedModel), resultHandler, errorMessageHandler);
    }

    static Class<TradeModel> getSharedModelClass(TradeModel sharedModel) {
        //noinspection unchecked
        return (Class<TradeModel>) sharedModel.getClass().getSuperclass().getSuperclass();
    }
}

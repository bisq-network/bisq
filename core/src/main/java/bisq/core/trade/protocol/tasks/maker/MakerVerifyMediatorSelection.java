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

package bisq.core.trade.protocol.tasks.maker;

import bisq.core.trade.Trade;
import bisq.core.trade.protocol.MediatorSelectionRule;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MakerVerifyMediatorSelection extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public MakerVerifyMediatorSelection(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            final NodeAddress selectedAddress = MediatorSelectionRule.select(
                    processModel.getTakerAcceptedMediatorNodeAddresses(),
                    processModel.getOffer());
            if (trade.getMediatorNodeAddress() != null &&
                    trade.getMediatorNodeAddress().equals(selectedAddress))
                complete();
            else
                failed("Mediator selection verification failed");
        } catch (Throwable t) {
            failed(t);
        }
    }
}

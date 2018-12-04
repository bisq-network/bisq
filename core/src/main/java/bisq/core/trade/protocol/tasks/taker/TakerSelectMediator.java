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

package bisq.core.trade.protocol.tasks.taker;

import bisq.core.trade.Trade;
import bisq.core.trade.protocol.MediatorSelectionRule;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerSelectMediator extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerSelectMediator(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            List<NodeAddress> acceptedMediatorAddresses = processModel.getUser().getAcceptedMediatorAddresses();
            checkNotNull(acceptedMediatorAddresses, "acceptedMediatorAddresses must not be null");
            checkArgument(!acceptedMediatorAddresses.isEmpty(), "acceptedMediatorAddresses must not be empty");
            NodeAddress mediatorNodeAddress;
            try {
                mediatorNodeAddress = MediatorSelectionRule.select(acceptedMediatorAddresses, processModel.getOffer());
            } catch (Throwable t) {
                // In case the mediator from the offer is not available anymore we just use the first.
                // Mediators are not implemented anyway and we will remove it with the new trade protocol but we
                // still need to be backward compatible so we cannot remove it now.
                mediatorNodeAddress = acceptedMediatorAddresses.get(0);
            }

            trade.setMediatorNodeAddress(mediatorNodeAddress);
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

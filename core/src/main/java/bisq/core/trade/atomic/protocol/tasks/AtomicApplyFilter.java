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

package bisq.core.trade.atomic.protocol.tasks;

import bisq.core.filter.FilterManager;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.TradeUtil;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicApplyFilter extends AtomicTradeTask {
    public AtomicApplyFilter(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            NodeAddress nodeAddress = checkNotNull(atomicProcessModel.getTempTradingPeerNodeAddress());
            @Nullable
            PaymentAccountPayload paymentAccountPayload =
                    atomicProcessModel.getTradingPeer().getPaymentAccountPayload();

            FilterManager filterManager = atomicProcessModel.getFilterManager();

            TradeUtil.applyFilter(atomicTrade,
                    filterManager,
                    nodeAddress,
                    paymentAccountPayload,
                    this::complete,
                    this::failed);
        } catch (Throwable t) {
            failed(t);
        }
    }
}


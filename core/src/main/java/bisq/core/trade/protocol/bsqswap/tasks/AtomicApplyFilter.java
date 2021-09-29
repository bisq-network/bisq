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

package bisq.core.trade.protocol.bsqswap.tasks;

import bisq.core.filter.FilterManager;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.TradeUtil;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.trade.tasks.AtomicTradeTask;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicApplyFilter extends AtomicTradeTask {
    public AtomicApplyFilter(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            NodeAddress nodeAddress = checkNotNull(bsqSwapProtocolModel.getTempTradingPeerNodeAddress());
            @Nullable
            PaymentAccountPayload paymentAccountPayload =
                    bsqSwapProtocolModel.getTradingPeer().getPaymentAccountPayload();

            FilterManager filterManager = bsqSwapProtocolModel.getFilterManager();

            TradeUtil.applyFilter(bsqSwapTrade,
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


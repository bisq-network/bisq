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

package bisq.core.trade.protocol.bsq_swap.tasks.buyer_as_maker;

import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.tasks.buyer.ProcessBsqSwapFinalizeTxRequest;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerAsMakerProcessBsqSwapFinalizeTxRequest extends ProcessBsqSwapFinalizeTxRequest {
    @SuppressWarnings({"unused"})
    public BuyerAsMakerProcessBsqSwapFinalizeTxRequest(TaskRunner<BsqSwapTrade> taskHandler,
                                                       BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            super.run();
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected long getSellersTradeFee() {
        return trade.getTakerFeeAsLong();
    }

    @Override
    protected long getBuyersTradeFee() {
        return trade.getMakerFeeAsLong();
    }
}

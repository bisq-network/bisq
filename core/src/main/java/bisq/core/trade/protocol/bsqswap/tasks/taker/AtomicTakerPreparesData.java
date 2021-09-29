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

package bisq.core.trade.protocol.bsqswap.tasks.taker;

import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.AtomicTradeTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicTakerPreparesData extends AtomicTradeTask {

    @SuppressWarnings({"unused"})
    public AtomicTakerPreparesData(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkArgument(!bsqSwapProtocolModel.getOffer().isMyOffer(bsqSwapProtocolModel.getKeyRing()), "must not take own offer");

            bsqSwapProtocolModel.initFromTrade(bsqSwapTrade);
            bsqSwapProtocolModel.setTakerBsqAddress(
                    bsqSwapProtocolModel.getBsqWalletService().getUnusedAddress().toString());
            bsqSwapProtocolModel.setTakerBtcAddress(
                    bsqSwapProtocolModel.getBtcWalletService().getFreshAddressEntry().getAddressString());

            // Set mining fee and init AtomicTxBuilder
            var feeService = bsqSwapProtocolModel.getProvider().getFeeService();
            feeService.requestFees(() -> {
                        bsqSwapProtocolModel.setTxFeePerVbyte(feeService.getTxFeePerVbyte().getValue());
                        bsqSwapProtocolModel.initTxBuilder(false);
                        complete();
                    },
                    (String errorMessage, Throwable throwable) -> {
                        if (throwable != null) {
                            failed(throwable);
                        } else if (!errorMessage.isEmpty()) {
                            failed(errorMessage);
                        } else {
                            failed();
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}

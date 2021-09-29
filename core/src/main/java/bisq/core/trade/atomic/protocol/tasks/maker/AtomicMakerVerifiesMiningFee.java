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

package bisq.core.trade.atomic.protocol.tasks.maker;

import bisq.core.trade.atomic.messages.CreateAtomicTxRequest;
import bisq.core.trade.atomic.protocol.tasks.AtomicSetupTxListener;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.abs;

@Slf4j
public class AtomicMakerVerifiesMiningFee extends AtomicSetupTxListener {

    @SuppressWarnings({"unused"})
    public AtomicMakerVerifiesMiningFee(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            var message = (CreateAtomicTxRequest) bsqSwapProtocolModel.getTradeMessage();

            // Verify mining fee
            var feeService = bsqSwapProtocolModel.getProvider().getFeeService();
            feeService.requestFees(() -> {
                var myFee = feeService.getTxFeePerVbyte();
                var peerFee = Coin.valueOf(message.getTxFeePerVbyte());
                        checkArgument(isAcceptableTxFee(myFee, peerFee),
                                "Miner fee disagreement, myFee={} peerFee={}", myFee, peerFee);
                        bsqSwapProtocolModel.setTxFeePerVbyte(peerFee.getValue());
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

    private boolean isAcceptableTxFee(Coin myFee, Coin peerFee) {
        var fee1 = (double) myFee.getValue();
        var fee2 = (double) peerFee.getValue();
        // Allow for 10% diff in mining fee, ie, maker will accept taker fee that's 10%
        // off their own fee from service. Both parties will use the same fee while
        // creating the atomic tx
        return abs(1 - fee1 / fee2) < 0.1;
    }

}

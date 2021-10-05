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

package bisq.core.trade.protocol.bsq_swap.tasks.buyer;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerPublishesTx extends BsqSwapTask {
    @SuppressWarnings({"unused"})
    public BuyerPublishesTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            protocolModel.getWalletsManager().publishAndCommitBsqTx(protocolModel.getTransaction(),
                    TxType.TRANSFER_BSQ,
                    new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            if (!completed) {
                                trade.setState(BsqSwapTrade.State.COMPLETED);
                                protocolModel.getTradeManager().onBsqSwapTradeCompleted(trade);

                                complete();
                            } else {
                                log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                            }
                        }

                        @Override
                        public void onFailure(TxBroadcastException exception) {
                            if (!completed) {
                                failed(exception);
                            } else {
                                log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                            }
                        }
                    });
        } catch (Throwable t) {
            failed(t);
        }
    }
}

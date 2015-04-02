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

package io.bitsquare.trade.protocol.trade.buyer.taker.tasks;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerAsBuyerTrade;
import io.bitsquare.trade.TakerAsSellerTrade;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerSignsAndPublishDepositTx extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerSignsAndPublishDepositTx.class);

    public TakerSignsAndPublishDepositTx(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            Coin inputAmount = takerTrade.getSecurityDeposit().add(FeePolicy.TX_FEE);
            
            takerTradeProcessModel.getTradeWalletService().signAndPublishDepositTx(
                    takerTradeProcessModel.tradingPeer.getPreparedDepositTx(),
                    takerTradeProcessModel.getConnectedOutputsForAllInputs(),
                    takerTradeProcessModel.tradingPeer.getConnectedOutputsForAllInputs(),
                    takerTradeProcessModel.getOutputs(),
                    inputAmount,
                    takerTradeProcessModel.getTradeWalletPubKey(),
                    takerTradeProcessModel.tradingPeer.getTradeWalletPubKey(),
                    takerTradeProcessModel.getArbitratorPubKey(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.trace("takerSignAndPublishTx succeeded " + transaction);

                            takerTrade.setDepositTx(transaction);

                            if (takerTrade instanceof TakerAsBuyerTrade) {
                                takerTrade.setProcessState(TakerAsBuyerTrade.ProcessState.DEPOSIT_PUBLISHED);
                                takerTrade.setLifeCycleState(TakerAsBuyerTrade.LifeCycleState.PENDING);
                            }
                            else if (takerTrade instanceof TakerAsSellerTrade) {
                                takerTrade.setProcessState(TakerAsSellerTrade.ProcessState.DEPOSIT_PUBLISHED);
                                takerTrade.setLifeCycleState(TakerAsSellerTrade.LifeCycleState.PENDING);
                            }

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            t.printStackTrace();
                            takerTrade.setThrowable(t);
                            failed(t);
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}

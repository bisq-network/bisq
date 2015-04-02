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

package io.bitsquare.trade.protocol.trade.buyer.offerer.tasks;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.OffererState;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererSignsAndPublishDepositTx extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererSignsAndPublishDepositTx.class);

    public OffererSignsAndPublishDepositTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            Coin inputAmount = trade.getSecurityDeposit().add(FeePolicy.TX_FEE);

            processModel.getTradeWalletService().signAndPublishDepositTx(
                    processModel.tradingPeer.getPreparedDepositTx(),
                    processModel.getConnectedOutputsForAllInputs(),
                    processModel.tradingPeer.getConnectedOutputsForAllInputs(),
                    processModel.getOutputs(),
                    inputAmount,
                    processModel.getTradeWalletPubKey(),
                    processModel.tradingPeer.getTradeWalletPubKey(),
                    processModel.getArbitratorPubKey(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.trace("offererSignAndPublishTx succeeded " + transaction);

                            trade.setDepositTx(transaction);

                            if (trade instanceof OffererAsBuyerTrade) {
                                trade.setProcessState(OffererState.ProcessState.DEPOSIT_PUBLISHED);
                                trade.setLifeCycleState(OffererState.LifeCycleState.PENDING);
                            }
                            else if (trade instanceof OffererAsSellerTrade) {
                                trade.setProcessState(OffererState.ProcessState.DEPOSIT_PUBLISHED);
                                trade.setLifeCycleState(OffererState.LifeCycleState.PENDING);
                            }

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            t.printStackTrace();
                            trade.setThrowable(t);

                            if (trade instanceof OffererAsBuyerTrade)
                                trade.setLifeCycleState(OffererState.LifeCycleState.OFFER_OPEN);
                            else if (trade instanceof OffererAsSellerTrade)
                                trade.setLifeCycleState(OffererState.LifeCycleState.OFFER_OPEN);

                            failed(t);
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);

            if (trade instanceof OffererAsBuyerTrade)
                trade.setLifeCycleState(OffererState.LifeCycleState.OFFER_OPEN);
            else if (trade instanceof OffererAsSellerTrade)
                trade.setLifeCycleState(OffererState.LifeCycleState.OFFER_OPEN);

            failed(t);
        }
    }
}

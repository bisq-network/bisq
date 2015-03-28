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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererTrade;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishDepositTx extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTx.class);

    public SignAndPublishDepositTx(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            Coin offererInputAmount = offererTrade.getSecurityDeposit().add(FeePolicy.TX_FEE);
            offererTradeProcessModel.getTradeWalletService().offererSignsAndPublishDepositTx(
                    offererTradeProcessModel.taker.getPreparedDepositTx(),
                    offererTradeProcessModel.offerer.getConnectedOutputsForAllInputs(),
                    offererTradeProcessModel.taker.getConnectedOutputsForAllInputs(),
                    offererTradeProcessModel.offerer.getOutputs(),
                    offererInputAmount,
                    offererTradeProcessModel.offerer.getTradeWalletPubKey(),
                    offererTradeProcessModel.taker.getTradeWalletPubKey(),
                    offererTradeProcessModel.getArbitratorPubKey(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.trace("offererSignAndPublishTx succeeded " + transaction);

                            offererTrade.setDepositTx(transaction);
                            offererTrade.setProcessState(OffererTrade.OffererProcessState.DEPOSIT_PUBLISHED);
                            offererTrade.setLifeCycleState(OffererTrade.OffererLifeCycleState.PENDING);

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            t.printStackTrace();
                            offererTrade.setThrowable(t);
                            offererTrade.setLifeCycleState(OffererTrade.OffererLifeCycleState.OFFER_OPEN);
                            failed(t);
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            offererTrade.setLifeCycleState(OffererTrade.OffererLifeCycleState.OFFER_OPEN);
            failed(t);
        }
    }
}

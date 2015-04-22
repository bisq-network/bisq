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

package io.bitsquare.trade.protocol.trade.tasks.buyer;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeTask;
import io.bitsquare.trade.states.BuyerTradeState;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import java.util.Date;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishDepositTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTx.class);

    public SignAndPublishDepositTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
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
                            log.trace("takerSignAndPublishTx succeeded " + transaction);

                            trade.setDepositTx(transaction);

                            trade.setLifeCycleState(Trade.LifeCycleState.PENDING);
                            trade.setProcessState(BuyerTradeState.ProcessState.DEPOSIT_PUBLISHED);
                            trade.setTakeOfferDate(new Date());

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            handleFault(t);
                        }
                    });
        } catch (Throwable t) {
            handleFault(t);
        }
    }

    private void handleFault(Throwable t) {
        t.printStackTrace();
        trade.setThrowable(t);

        if (trade instanceof BuyerTrade)
            trade.setLifeCycleState(Trade.LifeCycleState.PREPARATION);

        failed(t);
    }
}

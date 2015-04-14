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

package io.bitsquare.trade.protocol.trade.tasks.taker;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeTask;

import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroadcastTakeOfferFeeTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(BroadcastTakeOfferFeeTx.class);

    public BroadcastTakeOfferFeeTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            processModel.getTradeWalletService().broadcastTx(processModel.getTakeOfferFeeTx(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.debug("Take offer fee published successfully. Transaction ID = " + transaction.getHashAsString());

                           /* if (trade instanceof SellerTrade)
                                trade.setProcessState(TakerTradeState.ProcessState.TAKE_OFFER_FEE_PUBLISHED);*/
                            
                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            t.printStackTrace();
                            appendToErrorMessage("Take offer fee payment failed. Maybe your network connection was lost. Please try again.");
                            trade.setErrorMessage(errorMessage);

                          /*  if (trade instanceof SellerTrade)
                                trade.setProcessState(TakerTradeState.ProcessState.TAKE_OFFER_FEE_PUBLISH_FAILED);*/

                            failed(t);
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}

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

package io.bitsquare.trade.protocol.trade.shared.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerAsBuyerTrade;
import io.bitsquare.trade.TakerAsSellerTrade;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroadcastTakeOfferFeeTx extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(BroadcastTakeOfferFeeTx.class);

    public BroadcastTakeOfferFeeTx(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            takerTradeProcessModel.getTradeWalletService().broadcastTakeOfferFeeTx(takerTradeProcessModel.getTakeOfferFeeTx(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.debug("Take offer fee published successfully. Transaction ID = " + transaction.getHashAsString());

                            if (takerTrade instanceof TakerAsBuyerTrade)
                                takerTrade.setProcessState(TakerAsBuyerTrade.ProcessState.TAKE_OFFER_FEE_PUBLISHED);
                            else if (takerTrade instanceof TakerAsSellerTrade)
                                takerTrade.setProcessState(TakerAsSellerTrade.ProcessState.TAKE_OFFER_FEE_PUBLISHED);
                                complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            t.printStackTrace();
                            appendToErrorMessage("Take offer fee payment failed. Maybe your network connection was lost. Please try again.");
                            takerTrade.setErrorMessage(errorMessage);

                            if (takerTrade instanceof TakerAsBuyerTrade)
                                takerTrade.setProcessState(TakerAsBuyerTrade.ProcessState.TAKE_OFFER_FEE_PUBLISH_FAILED);
                            else if (takerTrade instanceof TakerAsSellerTrade)
                                takerTrade.setProcessState(TakerAsSellerTrade.ProcessState.TAKE_OFFER_FEE_PUBLISH_FAILED);

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

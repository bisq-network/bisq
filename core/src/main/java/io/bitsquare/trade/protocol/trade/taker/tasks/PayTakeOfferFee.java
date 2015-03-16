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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayTakeOfferFee extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(PayTakeOfferFee.class);

    public PayTakeOfferFee(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            model.getWalletService().payTakeOfferFee(model.getId(), new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.debug("Take offer fee paid successfully. Transaction ID = " + transaction.getHashAsString());
                    model.getTrade().setTakeOfferFeeTxID(transaction.getHashAsString());
                    model.getTrade().setState(Trade.State.TAKE_OFFER_FEE_PAID);
                    
                    complete();
                }

                @Override
                public void onFailure(@NotNull Throwable t) {
                    failed(t);
                }
            });
        } catch (InsufficientMoneyException e) {
            appendToErrorMessage(e.getMessage());
            failed(e);
        }
    }

    @Override
    protected void updateStateOnFault() {
        // As long as the take offer fee was not paid nothing critical happens.
        // The take offer process can be repeated so we reset the trade state.
        appendToErrorMessage("Take offer fee payment failed. Maybe your network connection was lost. Please try again.");
        model.getTrade().setTakeOfferFeeTxID(null);
        model.getTrade().setState(Trade.State.TAKE_OFFER_FEE_PAYMENT_FAILED);
    }
}

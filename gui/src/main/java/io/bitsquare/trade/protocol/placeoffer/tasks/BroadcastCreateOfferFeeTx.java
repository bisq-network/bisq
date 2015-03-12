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

package io.bitsquare.trade.protocol.placeoffer.tasks;

import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;
import io.bitsquare.util.tasks.Task;
import io.bitsquare.util.tasks.TaskRunner;

import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroadcastCreateOfferFeeTx extends Task<PlaceOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(BroadcastCreateOfferFeeTx.class);

    public BroadcastCreateOfferFeeTx(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            model.getWalletService().broadcastCreateOfferFeeTx(model.getTransaction(), new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.info("Broadcast of offer fee payment succeeded: transaction = " + transaction.toString());
                    if (transaction == null)
                        failed("Broadcast of offer fee payment failed because transaction = null.");
                    else
                        complete();
                }

                @Override
                public void onFailure(Throwable t) {
                    failed(t);
                }
            });
        } catch (Throwable t) {
            failed(t);
        }
    }
}

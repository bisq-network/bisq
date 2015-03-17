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
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishPayoutTx extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishPayoutTx.class);

    public SignAndPublishPayoutTx(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            model.getTradeWalletService().takerSignsAndPublishPayoutTx(
                    model.getPublishedDepositTx(),
                    model.getOffererSignature(),
                    model.getOffererPayoutAmount(),
                    model.getTakerPayoutAmount(),
                    model.getOffererPayoutAddress(),
                    model.getAddressEntry(),
                    model.getOffererPubKey(),
                    model.getTakerPubKey(),
                    model.getArbitratorPubKey(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            model.setPayoutTx(transaction);
                            model.getTrade().setState(Trade.State.PAYOUT_PUBLISHED);

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            failed(t);
                        }
                    });
        } catch (Throwable e) {
            failed(e);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}

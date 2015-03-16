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
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererModel;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishDepositTx extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTx.class);

    public SignAndPublishDepositTx(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            Coin offererInputAmount = model.getTrade().getSecurityDeposit().add(FeePolicy.TX_FEE);
            model.getWalletService().offererSignsAndPublishTx(
                    model.getTakerDepositTx(),
                    model.getOffererConnectedOutputsForAllInputs(),
                    model.getTakerConnectedOutputsForAllInputs(),
                    model.getOffererOutputs(),
                    offererInputAmount,
                    model.getOffererPubKey(),
                    model.getTakerPubKey(),
                    model.getArbitratorPubKey(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.trace("offererSignAndPublishTx succeeded " + transaction);

                            model.setPublishedDepositTx(transaction);
                            model.getTrade().setDepositTx(transaction);
                            model.getTrade().setState(Trade.State.DEPOSIT_PUBLISHED);

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            failed(t);
                        }
                    });
        } catch (Exception e) {
            failed(e);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}

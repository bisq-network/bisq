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

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerTrade;

import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishPayoutTx extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishPayoutTx.class);

    public SignAndPublishPayoutTx(TaskRunner taskHandler, TakerTrade model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            takerTradeProcessModel.tradeWalletService.takerSignsAndPublishPayoutTx(
                    takerTrade.getDepositTx(),
                    takerTradeProcessModel.offerer.signature,
                    takerTradeProcessModel.offerer.payoutAmount,
                    takerTradeProcessModel.taker.payoutAmount,
                    takerTradeProcessModel.offerer.payoutAddressString,
                    takerTradeProcessModel.taker.addressEntry,
                    takerTradeProcessModel.offerer.tradeWalletPubKey,
                    takerTradeProcessModel.taker.tradeWalletPubKey,
                    takerTradeProcessModel.arbitratorPubKey,
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            takerTradeProcessModel.setPayoutTx(transaction);
                            takerTrade.setProcessState(TakerTrade.TakerProcessState.PAYOUT_PUBLISHED);

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            takerTrade.setThrowable(t);
                            failed(t);
                        }
                    });
        } catch (Throwable t) {
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}

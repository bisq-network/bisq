/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.taker;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class TakerPublishTakerFeeTx extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerPublishTakerFeeTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Timer timeoutTimer = UserThread.runAfter(() -> {
                log.warn("Broadcast not completed after 5 sec. We go on with the trade protocol.");
                trade.setState(Trade.State.TAKER_PUBLISHED_TAKER_FEE_TX);
                complete();
            }, 5);

            processModel.getTradeWalletService().broadcastTx(processModel.resolveTakeOfferFeeTx(trade),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            if (!completed) {
                                timeoutTimer.stop();
                                log.debug("Trading fee published successfully. Transaction ID = " + transaction.getHashAsString());
                                trade.setState(Trade.State.TAKER_PUBLISHED_TAKER_FEE_TX);
                                complete();
                            } else {
                                log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                            }
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            if (!completed) {
                                timeoutTimer.stop();
                                appendToErrorMessage("Trading fee payment failed. Maybe your network connection was lost. Please try again.");
                                failed(t);
                            } else {
                                log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                            }
                        }
                    });
        } catch (Throwable t) {
            failed(t);
        }
    }
}

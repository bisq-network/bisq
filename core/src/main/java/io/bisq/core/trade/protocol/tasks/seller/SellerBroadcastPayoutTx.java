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

package io.bisq.core.trade.protocol.tasks.seller;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerBroadcastPayoutTx extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public SellerBroadcastPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Transaction payoutTx = trade.getPayoutTx();
            checkNotNull(payoutTx, "payoutTx must not be null");

            TransactionConfidence.ConfidenceType confidenceType = payoutTx.getConfidence().getConfidenceType();
            log.debug("payoutTx confidenceType:" + confidenceType);
            if (confidenceType.equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                    confidenceType.equals(TransactionConfidence.ConfidenceType.PENDING)) {
                log.debug("payoutTx was already published. confidenceType:" + confidenceType);
                trade.setState(Trade.State.SELLER_PUBLISHED_PAYOUT_TX);
                complete();
            } else {
                Timer timeoutTimer = UserThread.runAfter(() -> {
                    log.warn("Broadcast not completed after 5 sec. We go on with the trade protocol.");
                    trade.setState(Trade.State.SELLER_PUBLISHED_PAYOUT_TX);
                    complete();
                }, 5);
                processModel.getTradeWalletService().broadcastTx(payoutTx,
                        new FutureCallback<Transaction>() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                if (!completed) {
                                    timeoutTimer.stop();
                                    log.debug("BroadcastTx succeeded. Transaction:" + transaction);
                                    trade.setState(Trade.State.SELLER_PUBLISHED_PAYOUT_TX);
                                    complete();
                                } else {
                                    log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                                }
                            }

                            @Override
                            public void onFailure(@NotNull Throwable t) {
                                if (!completed) {
                                    timeoutTimer.stop();
                                    log.error("BroadcastTx failed. Error:" + t.getMessage());
                                    failed(t);
                                } else {
                                    log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                                }
                            }
                        });
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}

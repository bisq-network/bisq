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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class BroadcastPayoutTx extends TradeTask {
    public BroadcastPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    protected abstract void setState();

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
                setState();
                complete();
            } else {
                processModel.getTradeWalletService().broadcastTx(payoutTx,
                        new TxBroadcaster.Callback() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                if (!completed) {
                                    log.debug("BroadcastTx succeeded. Transaction:" + transaction);
                                    setState();
                                    complete();
                                } else {
                                    log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                                }
                            }

                            @Override
                            public void onFailure(TxBroadcastException exception) {
                                if (!completed) {
                                    log.error("BroadcastTx failed. Error:" + exception.getMessage());
                                    failed(exception);
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

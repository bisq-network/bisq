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

package bisq.core.trade.protocol.tasks.taker;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class TakerPublishFeeTx extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerPublishFeeTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            Transaction takeOfferFeeTx = processModel.getTakeOfferFeeTx();

            if (trade.isCurrencyForTakerFeeBtc()) {
                // We committed to be sure the tx gets into the wallet even in the broadcast process it would be
                // committed as well, but if user would close app before success handler returns the commit would not
                // be done.
                tradeWalletService.commitTx(takeOfferFeeTx);

                tradeWalletService.broadcastTx(takeOfferFeeTx,
                        new TxBroadcaster.Callback() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                trade.setState(Trade.State.TAKER_PUBLISHED_TAKER_FEE_TX);
                                complete();
                            }

                            @Override
                            public void onFailure(TxBroadcastException exception) {
                                failed(exception);
                            }
                        });
            } else {
                BsqWalletService bsqWalletService = processModel.getBsqWalletService();
                bsqWalletService.commitTx(takeOfferFeeTx, TxType.PAY_TRADE_FEE);
                // We need to create another instance, otherwise the tx would trigger an invalid state exception
                // if it gets committed 2 times
                tradeWalletService.commitTx(tradeWalletService.getClonedTransaction(takeOfferFeeTx));

                bsqWalletService.broadcastTx(takeOfferFeeTx,
                        new TxBroadcaster.Callback() {
                            @Override
                            public void onSuccess(@Nullable Transaction transaction) {
                                if (!completed) {
                                    if (transaction != null) {
                                        trade.setTakerFeeTxId(transaction.getHashAsString());
                                        processModel.setTakeOfferFeeTx(transaction);
                                        trade.setState(Trade.State.TAKER_PUBLISHED_TAKER_FEE_TX);

                                        complete();
                                    }
                                } else {
                                    log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                                }
                            }

                            @Override
                            public void onFailure(TxBroadcastException exception) {
                                if (!completed) {
                                    log.error(exception.toString());
                                    exception.printStackTrace();
                                    trade.setErrorMessage("An error occurred.\n" +
                                            "Error message:\n"
                                            + exception.getMessage());
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

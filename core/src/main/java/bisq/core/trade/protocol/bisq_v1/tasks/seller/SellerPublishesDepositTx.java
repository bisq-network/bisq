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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.DepositTxValidation.checkCanonicalDepositTxFields;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerPublishesDepositTx extends TradeTask {
    public SellerPublishesDepositTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            if (trade.isDepositPublished()) {
                complete();
                return;
            }
            if (checkNotNull(trade.getOffer()).isFiatOffer() &&
                    (!processModel.isBuyerPaymentAccountValidated() ||
                            !processModel.isDepositTxAndDelayedPayoutTxMessageDelivered())) {
                log.info("Not publishing fiat deposit until the buyer account is validated and the deposit message is delivered");
                complete();
                return;
            }

            Transaction depositTx = checkCanonicalDepositTxFields(processModel.getDepositTx());
            if (!processModel.tryStartDepositTxBroadcast()) {
                log.info("A broadcast of the deposit tx is already pending, so we do not broadcast it again.");
                complete();
                return;
            }

            broadcastDepositTx(depositTx);
        } catch (Throwable t) {
            failed(t);
        }
    }

    private void broadcastDepositTx(Transaction depositTx) {
        try {
            processModel.getTradeWalletService().broadcastTx(depositTx,
                    new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            processModel.finishDepositTxBroadcast();
                            if (completed) {
                                log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                                return;
                            }

                            // Now as we have published the deposit tx we set it in trade
                            trade.applyDepositTx(depositTx);

                            if (!trade.isDepositConfirmed()) {
                                trade.setState(Trade.State.SELLER_PUBLISHED_DEPOSIT_TX);
                            }

                            processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(processModel.getOffer().getId(),
                                    AddressEntry.Context.RESERVED_FOR_TRADE);

                            processModel.getTradeManager().requestPersistence();

                            complete();
                        }

                        @Override
                        public void onFailure(TxBroadcastException exception) {
                            processModel.finishDepositTxBroadcast();
                            if (completed) {
                                log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                                return;
                            }

                            failed(exception);
                        }
                    });
        } catch (Throwable t) {
            processModel.finishDepositTxBroadcast();
            throw t;
        }
    }
}

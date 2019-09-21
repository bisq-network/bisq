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

package bisq.core.trade.protocol.tasks.seller;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerPublishesDepositTx extends TradeTask {
    @SuppressWarnings({"unused"})
    public SellerPublishesDepositTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            processModel.getTradeWalletService().broadcastTx(trade.getDepositTx(),
                    new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            if (!completed) {
                                trade.setState(Trade.State.SELLER_PUBLISHED_DEPOSIT_TX);

                                processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(processModel.getOffer().getId(), AddressEntry.Context.RESERVED_FOR_TRADE);

                                complete();
                            } else {
                                log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                            }
                        }

                        @Override
                        public void onFailure(TxBroadcastException exception) {
                            if (!completed) {
                                failed(exception);
                            } else {
                                log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                            }
                        }
                    });
        } catch (Throwable t) {
            Contract contract = trade.getContract();
            if (contract != null)
                contract.printDiff(processModel.getTradingPeer().getContractAsJson());
            failed(t);
        }
    }
}

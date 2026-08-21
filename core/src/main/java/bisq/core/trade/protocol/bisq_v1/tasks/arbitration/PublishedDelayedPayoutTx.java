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

package bisq.core.trade.protocol.bisq_v1.tasks.arbitration;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublishedDelayedPayoutTx extends TradeTask {
    public PublishedDelayedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Transaction delayedPayoutTx = trade.getDelayedPayoutTx();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();

            // We have spent the funds from the deposit tx with the delayedPayoutTx
            btcWalletService.resetCoinLockedInMultiSigAddressEntry(trade.getId());
            // We might receive funds on AddressEntry.Context.TRADE_PAYOUT so we don't swap that

            Transaction committedDelayedPayoutTx = WalletService.maybeAddSelfTxToWallet(delayedPayoutTx, btcWalletService.getWallet());

            processModel.getTradeWalletService().broadcastTx(committedDelayedPayoutTx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.info("publishDelayedPayoutTx onSuccess " + transaction);
                    complete();
                }

                @Override
                public void onFailure(TxBroadcastException exception) {
                    log.error("publishDelayedPayoutTx onFailure", exception);
                    failed(exception.toString());
                }
            });
        } catch (Throwable t) {
            failed(t);
        }
    }
}

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

package bisq.core.trade.protocol.tasks.buyer;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.PayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerProcessPayoutTxPublishedMessage extends TradeTask {
    @SuppressWarnings({"unused"})
    public BuyerProcessPayoutTxPublishedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            PayoutTxPublishedMessage message = (PayoutTxPublishedMessage) processModel.getTradeMessage();
            Validator.checkTradeId(processModel.getOfferId(), message);
            checkNotNull(message);
            checkArgument(message.getPayoutTx() != null);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            if (trade.getPayoutTx() == null) {
                Transaction committedPayoutTx = WalletService.maybeAddNetworkTxToWallet(message.getPayoutTx(), processModel.getBtcWalletService().getWallet());
                trade.setPayoutTx(committedPayoutTx);
                BtcWalletService.printTx("payoutTx received from peer", committedPayoutTx);

                trade.setState(Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG);
                processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
            } else {
                log.info("We got the payout tx already set from BuyerSetupPayoutTxListener and do nothing here. trade ID={}", trade.getId());
            }
            processModel.removeMailboxMessageAfterProcessing(trade);
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

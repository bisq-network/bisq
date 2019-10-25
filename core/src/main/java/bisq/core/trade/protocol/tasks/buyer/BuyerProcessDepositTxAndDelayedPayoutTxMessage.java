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
import bisq.core.trade.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerProcessDepositTxAndDelayedPayoutTxMessage extends TradeTask {
    @SuppressWarnings({"unused"})
    public BuyerProcessDepositTxAndDelayedPayoutTxMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            DepositTxAndDelayedPayoutTxMessage message = (DepositTxAndDelayedPayoutTxMessage) processModel.getTradeMessage();
            checkNotNull(message);
            Validator.checkTradeId(processModel.getOfferId(), message);
            checkArgument(message.getDepositTx() != null);

            // To access tx confidence we need to add that tx into our wallet.
            Transaction depositTx = processModel.getBtcWalletService().getTxFromSerializedTx(message.getDepositTx());
            // update with full tx
            Transaction committedDepositTx = WalletService.maybeAddSelfTxToWallet(depositTx, processModel.getBtcWalletService().getWallet());
            trade.applyDepositTx(committedDepositTx);
            BtcWalletService.printTx("depositTx received from peer", committedDepositTx);

            // To access tx confidence we need to add that tx into our wallet.
            byte[] delayedPayoutTxBytes = message.getDelayedPayoutTx();
            trade.applyDelayedPayoutTxBytes(delayedPayoutTxBytes);
            BtcWalletService.printTx("delayedPayoutTx received from peer", trade.getDelayedPayoutTx());

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.removeMailboxMessageAfterProcessing(trade);

            // If we got already the confirmation we don't want to apply an earlier state
            if (trade.getState() != Trade.State.BUYER_SAW_DEPOSIT_TX_IN_NETWORK)
                trade.setState(Trade.State.BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);

            processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

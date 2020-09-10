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

package bisq.core.trade.protocol.tasks.cancel;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.trade.HandleCancelTradeRequestState;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CancelTradeRequestAcceptedMessage;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.util.Validator;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class ProcessCancelTradeRequestAcceptedMessage extends TradeTask {
    @SuppressWarnings({"unused"})
    public ProcessCancelTradeRequestAcceptedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            CancelTradeRequestAcceptedMessage message = (CancelTradeRequestAcceptedMessage) processModel.getTradeMessage();
            Validator.checkTradeId(processModel.getOfferId(), message);
            checkNotNull(message);
            checkArgument(message.getPayoutTx() != null);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            if (trade.getPayoutTx() == null) {
                Transaction committedCanceledTradePayoutTx = WalletService.maybeAddNetworkTxToWallet(message.getPayoutTx(), processModel.getBtcWalletService().getWallet());
                trade.setPayoutTx(committedCanceledTradePayoutTx);
                BtcWalletService.printTx("CanceledTradePayoutTx received from peer", committedCanceledTradePayoutTx);

                trade.setHandleCancelTradeRequestState(HandleCancelTradeRequestState.RECEIVED_ACCEPTED_MSG);

                // We need to delay that call as we might get executed at startup after mailbox messages are
                // applied where we iterate over our pending trades. The closeCanceledTrade method would remove
                // that trade from the list causing a ConcurrentModificationException.
                // To avoid that we delay for one render frame.
                UserThread.execute(() -> processModel.getTradeManager().closeCanceledTrade(trade));

                processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
            } else {
                log.info("We got the payout tx already set from SetupCanceledTradePayoutTxListener and do nothing here. trade ID={}", trade.getId());
            }
            processModel.removeMailboxMessageAfterProcessing(trade);
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

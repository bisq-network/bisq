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

package io.bisq.core.trade.protocol.tasks.maker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.DepositTxPublishedMessage;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.core.util.Validator;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Transaction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MakerProcessDepositTxPublishedMessage extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public MakerProcessDepositTxPublishedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            DepositTxPublishedMessage message = (DepositTxPublishedMessage) processModel.getTradeMessage();
            Validator.checkTradeId(processModel.getOfferId(), message);
            checkNotNull(message);
            checkArgument(message.getDepositTx() != null);

            // To access tx confidence we need to add that tx into our wallet.
            Transaction txFromSerializedTx = processModel.getBtcWalletService().getTxFromSerializedTx(message.getDepositTx());
            // update with full tx
            Transaction walletTx = processModel.getTradeWalletService().addTxToWallet(txFromSerializedTx);
            trade.setDepositTx(walletTx);
            BtcWalletService.printTx("depositTx received from peer", walletTx);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.removeMailboxMessageAfterProcessing(trade);

            // If we got already the confirmation we don't want to apply an earlier state
            if (trade.getState() != Trade.State.MAKER_SAW_DEPOSIT_TX_IN_NETWORK)
                trade.setState(Trade.State.MAKER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);

            processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.tasks.offerer;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.messages.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.messages.util.Validator.checkTradeId;

public class ProcessDepositTxPublishedMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessDepositTxPublishedMessage.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public ProcessDepositTxPublishedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            DepositTxPublishedMessage message = (DepositTxPublishedMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);
            checkArgument(message.depositTx != null);

            // To access tx confidence we need to add that tx into our wallet.
            Transaction transactionFromSerializedTx = processModel.getWalletService().getTransactionFromSerializedTx(message.depositTx);
            // update with full tx
            trade.setDepositTx(processModel.getTradeWalletService().addTransactionToWallet(transactionFromSerializedTx));

            if (trade instanceof OffererTrade)
                processModel.getOpenOfferManager().closeOpenOffer(trade.getOffer());

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            removeMailboxMessageAfterProcessing();

            trade.setState(Trade.State.OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
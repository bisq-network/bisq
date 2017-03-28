/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.maker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.DepositTxPublishedMsg;
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
            DepositTxPublishedMsg message = (DepositTxPublishedMsg) processModel.getTradeMessage();
            Validator.checkTradeId(processModel.getId(), message);
            checkNotNull(message);
            checkArgument(message.depositTx != null);

            // To access tx confidence we need to add that tx into our wallet.
            Transaction txFromSerializedTx = processModel.getWalletService().getTxFromSerializedTx(message.depositTx);
            // update with full tx
            Transaction walletTx = processModel.getTradeWalletService().addTxToWallet(txFromSerializedTx);
            trade.setDepositTx(walletTx);
            BtcWalletService.printTx("depositTx received from peer", walletTx);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            removeMailboxMessageAfterProcessing();

            trade.setState(Trade.State.MAKER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
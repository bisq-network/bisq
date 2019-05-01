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

import bisq.core.trade.Trade;
import bisq.core.trade.messages.FiatReceivedMessage;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerProcessFiatReceivedMessage extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public BuyerProcessFiatReceivedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            FiatReceivedMessage message = (FiatReceivedMessage) processModel.getTradeMessage();
            Validator.checkTradeId(processModel.getOfferId(), message);
            checkNotNull(message);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            if (trade.getPayoutTx() == null && trade.getState().ordinal() < Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG.ordinal()) {
                // In case the date of the peer would be in the future we use our local current time.
                // Set time before state as state listeners might want to use the time...
                trade.setFiatReceivedDate(Math.min(new Date().getTime(), message.getFiatReceivedDate()));
                trade.setState(Trade.State.BUYER_RECEIVED_SELLERS_FIAT_PAYMENT_RECEIPT_CONFIRMATION);
            }
            processModel.removeMailboxMessageAfterProcessing(trade);
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

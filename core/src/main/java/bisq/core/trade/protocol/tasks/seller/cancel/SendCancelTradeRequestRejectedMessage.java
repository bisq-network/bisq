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

package bisq.core.trade.protocol.tasks.seller.cancel;

import bisq.core.trade.SellerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.messages.cancel.CancelTradeRequestRejectedMessage;
import bisq.core.trade.protocol.tasks.SendMailboxMessageTask;

import bisq.common.taskrunner.TaskRunner;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;


@Slf4j
public class SendCancelTradeRequestRejectedMessage extends SendMailboxMessageTask {
    private final SellerTrade sellerTrade;

    @SuppressWarnings({"unused"})
    public SendCancelTradeRequestRejectedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);

        checkArgument(trade instanceof SellerTrade);
        sellerTrade = ((SellerTrade) trade);
    }

    @Override
    protected TradeMessage getMessage(String id) {
        return new CancelTradeRequestRejectedMessage(
                id,
                processModel.getMyNodeAddress(),
                UUID.randomUUID().toString()
        );
    }

    @Override
    protected void setStateSent() {
        sellerTrade.setCancelTradeState(SellerTrade.CancelTradeState.REQUEST_REJECTED_MSG_SENT);
    }

    @Override
    protected void setStateArrived() {
        sellerTrade.setCancelTradeState(SellerTrade.CancelTradeState.REQUEST_REJECTED_MSG_ARRIVED);
    }

    @Override
    protected void setStateStoredInMailbox() {
        sellerTrade.setCancelTradeState(SellerTrade.CancelTradeState.REQUEST_REJECTED_MSG_IN_MAILBOX);
    }

    @Override
    protected void setStateFault() {
        sellerTrade.setCancelTradeState(SellerTrade.CancelTradeState.REQUEST_REJECTED_MSG_SEND_FAILED);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkArgument(!trade.isDisputed(), "A dispute has already started.");

            super.run();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

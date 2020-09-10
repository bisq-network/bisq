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
import bisq.core.trade.messages.cancel.CancelTradeRequestAcceptedMessage;
import bisq.core.trade.protocol.tasks.SendMailboxMessageTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;


@Slf4j
public class SendCancelTradeRequestAcceptedMessage extends SendMailboxMessageTask {
    @SuppressWarnings({"unused"})
    public SendCancelTradeRequestAcceptedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected TradeMessage getMessage(String id) {
        Transaction payoutTx = checkNotNull(trade.getPayoutTx(), "trade.getPayoutTx() must not be null");
        return new CancelTradeRequestAcceptedMessage(
                id,
                payoutTx.bitcoinSerialize(),
                processModel.getMyNodeAddress(),
                UUID.randomUUID().toString()
        );
    }

    @Override
    protected void setStateSent() {
        trade.setSellersCancelTradeState(SellerTrade.CancelTradeState.REQUEST_ACCEPTED_MSG_SENT);
    }

    @Override
    protected void setStateArrived() {
        trade.setSellersCancelTradeState(SellerTrade.CancelTradeState.REQUEST_ACCEPTED_MSG_ARRIVED);
    }

    @Override
    protected void setStateStoredInMailbox() {
        trade.setSellersCancelTradeState(SellerTrade.CancelTradeState.REQUEST_ACCEPTED_MSG_IN_MAILBOX);
    }

    @Override
    protected void setStateFault() {
        trade.setSellersCancelTradeState(SellerTrade.CancelTradeState.REQUEST_ACCEPTED_MSG_SEND_FAILED);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            if (trade.getPayoutTx() == null) {
                log.error("trade.getPayoutTx() = " + trade.getPayoutTx());
                failed("PayoutTx is null");
                return;
            }

            super.run();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

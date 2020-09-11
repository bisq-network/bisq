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
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.SendMailboxMessageTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerSendCounterCurrencyTransferStartedMessage extends SendMailboxMessageTask {
    @SuppressWarnings({"unused"})
    public BuyerSendCounterCurrencyTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected TradeMessage getMessage(String tradeId) {
        BtcWalletService walletService = processModel.getBtcWalletService();
        String id = processModel.getOfferId();
        AddressEntry payoutAddressEntry = walletService.getOrCreateAddressEntry(id,
                AddressEntry.Context.TRADE_PAYOUT);
        // We do not use a real unique ID here as we want to be able to re-send the exact same message in case the
        // peer does not respond with an ACK msg in a certain time interval. To avoid that we get dangling mailbox
        // messages where only the one which gets processed by the peer would be removed we use the same uid. All
        // other data stays the same when we re-send the message at any time later.
        String deterministicId = tradeId + processModel.getMyNodeAddress().getFullAddress();

        return new CounterCurrencyTransferStartedMessage(
                id,
                payoutAddressEntry.getAddressString(),
                processModel.getMyNodeAddress(),
                processModel.getPayoutTxSignature(),
                trade.getCounterCurrencyTxId(),
                trade.getCounterCurrencyExtraData(),
                deterministicId
        );
    }

    @Override
    protected void setStateSent() {
        trade.setState(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG);
    }

    @Override
    protected void setStateArrived() {
        trade.setState(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
    }

    @Override
    protected void setStateStoredInMailbox() {
        trade.setState(Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG);
    }

    @Override
    protected void setStateFault() {
        trade.setState(Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            super.run();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

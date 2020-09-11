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
import bisq.core.network.MessageState;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.SendMailboxMessageTask;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import javafx.beans.value.ChangeListener;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerSendCounterCurrencyTransferStartedMessage extends SendMailboxMessageTask {
    private static final long MAX_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(4);

    private ChangeListener<MessageState> listener;
    private Timer timer;
    private CounterCurrencyTransferStartedMessage counterCurrencyTransferStartedMessage;


    public BuyerSendCounterCurrencyTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected TradeMessage getMessage(String tradeId) {
        if (counterCurrencyTransferStartedMessage == null) {
            AddressEntry payoutAddressEntry = processModel.getBtcWalletService().getOrCreateAddressEntry(tradeId,
                    AddressEntry.Context.TRADE_PAYOUT);

            // We do not use a real unique ID here as we want to be able to re-send the exact same message in case the
            // peer does not respond with an ACK msg in a certain time interval. To avoid that we get dangling mailbox
            // messages where only the one which gets processed by the peer would be removed we use the same uid. All
            // other data stays the same when we re-send the message at any time later.
            String deterministicId = tradeId + processModel.getMyNodeAddress().getFullAddress();
            counterCurrencyTransferStartedMessage = new CounterCurrencyTransferStartedMessage(
                    tradeId,
                    payoutAddressEntry.getAddressString(),
                    processModel.getMyNodeAddress(),
                    processModel.getPayoutTxSignature(),
                    trade.getCounterCurrencyTxId(),
                    trade.getCounterCurrencyExtraData(),
                    deterministicId
            );
        }
        return counterCurrencyTransferStartedMessage;
    }

    @Override
    protected void setStateSent() {
        trade.setState(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG);
    }

    @Override
    protected void setStateArrived() {
        trade.setState(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
        stop();
    }

    @Override
    protected void setStateStoredInMailbox() {
        trade.setState(Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG);
        start();
    }

    @Override
    protected void setStateFault() {
        trade.setState(Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG);
        start();
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

    private void stop() {
        if (timer != null) {
            timer.stop();
            processModel.getPaymentStartedMessageStateProperty().removeListener(listener);
        }
    }

    // The listeners ensure we don't get GCed even we have completed the task.
    private void start() {
        if (timer != null) {
            return;
        }

        PaymentMethod paymentMethod = checkNotNull(trade.getOffer()).getPaymentMethod();
        // For instant trades with 1 hour we want a short interval, otherwise a few hours should be ok.
        long interval = Math.min(paymentMethod.getMaxTradePeriod() / 5, MAX_REFRESH_INTERVAL);
        interval = 1000;
        timer = UserThread.runPeriodically(() -> super.run(), interval, TimeUnit.MILLISECONDS);

        listener = (observable, oldValue, newValue) -> {
            // Once we receive an ACK from our msg we know the peer has received the msg and we stop.
            if (newValue == MessageState.ACKNOWLEDGED) {
                // Ensure listener construction is completed before remove call
                UserThread.execute(this::stop);
            }
        };
        processModel.getPaymentStartedMessageStateProperty().addListener(listener);
    }
}

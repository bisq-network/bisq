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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.network.MessageState;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.messages.TradeMailboxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.SendMailboxMessageTask;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import javafx.beans.value.ChangeListener;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * We send the buyer the deposit and delayed payout tx. We wait to receive a ACK message back and resend the message
 * in case that does not happen in 4 seconds or if the message was stored in mailbox or failed. We keep repeating that
 * with doubling the interval each time and until the MAX_RESEND_ATTEMPTS is reached. If never successful we fail and
 * do not continue the protocol with publishing the deposit tx. That way we avoid that a deposit tx is published but the
 * buyer does not have the delayed payout tx and would not be able to open arbitration.
 */
@Slf4j
public class SellerSendsDepositTxAndDelayedPayoutTxMessage extends SendMailboxMessageTask {
    private static final int MAX_RESEND_ATTEMPTS = 7;
    private int delayInSec = 4;
    private int resendCounter = 0;
    private DepositTxAndDelayedPayoutTxMessage message;
    private ChangeListener<MessageState> listener;
    private Timer timer;

    public SellerSendsDepositTxAndDelayedPayoutTxMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected TradeMailboxMessage getTradeMailboxMessage(String tradeId) {
        if (message == null) {
            // We do not use a real unique ID here as we want to be able to re-send the exact same message in case the
            // peer does not respond with an ACK msg in a certain time interval. To avoid that we get dangling mailbox
            // messages where only the one which gets processed by the peer would be removed we use the same uid. All
            // other data stays the same when we re-send the message at any time later.
            String deterministicId = tradeId + processModel.getMyNodeAddress().getFullAddress();
            PaymentAccountPayload sellerPaymentAccountPayload = processModel.getPaymentAccountPayload(trade);
            message = new DepositTxAndDelayedPayoutTxMessage(
                    deterministicId,
                    processModel.getOfferId(),
                    processModel.getMyNodeAddress(),
                    checkNotNull(processModel.getDepositTx()).bitcoinSerialize(),
                    checkNotNull(trade.getDelayedPayoutTx()).bitcoinSerialize(),
                    sellerPaymentAccountPayload);
        }
        return message;
    }

    @Override
    protected void setStateSent() {
        // we no longer set deprecated state (Trade.State.SELLER_SENT_DEPOSIT_TX_PUBLISHED_MSG);
        // see https://github.com/bisq-network/bisq/pull/5746#issuecomment-939879623
    }

    @Override
    protected void setStateArrived() {
        // we no longer set deprecated state (Trade.State.SELLER_SAW_ARRIVED_DEPOSIT_TX_PUBLISHED_MSG);
        // see https://github.com/bisq-network/bisq/pull/5746#issuecomment-939879623

        cleanup();
        // Complete is called in base class
    }

    // We override the default behaviour for onStoredInMailbox and do not call complete
    @Override
    protected void onStoredInMailbox() {
        setStateStoredInMailbox();
    }

    @Override
    protected void setStateStoredInMailbox() {
        // we no longer set deprecated state (Trade.State.SELLER_STORED_IN_MAILBOX_DEPOSIT_TX_PUBLISHED_MSG);
        // see https://github.com/bisq-network/bisq/pull/5746#issuecomment-939879623

        // The DepositTxAndDelayedPayoutTxMessage is a mailbox message as earlier we use only the deposit tx which can
        // be also received from the network once published.
        // Now we send the delayed payout tx as well and with that this message is mandatory for continuing the protocol.
        // We do not support mailbox message handling during the take offer process as it is expected that both peers
        // are online.
        // For backward compatibility and extra resilience we still keep DepositTxAndDelayedPayoutTxMessage as a
        // mailbox message but the stored in mailbox case is not expected and the seller would try to send the message again
        // in the hope to reach the buyer directly.
        if (!trade.isDepositConfirmed()) {
            tryToSendAgainLater();
        }
    }

    // We override the default behaviour for onFault and do not call appendToErrorMessage and failed
    @Override
    protected void onFault(String errorMessage, TradeMessage message) {
        setStateFault();
    }

    @Override
    protected void setStateFault() {
        // we no longer set deprecated state (Trade.State.SELLER_SEND_FAILED_DEPOSIT_TX_PUBLISHED_MSG);
        // see https://github.com/bisq-network/bisq/pull/5746#issuecomment-939879623
        if (!trade.isDepositConfirmed()) {
            tryToSendAgainLater();
        }
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            super.run();
        } catch (Throwable t) {
            failed(t);
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        if (timer != null) {
            timer.stop();
        }
        if (listener != null) {
            processModel.getPaymentStartedMessageStateProperty().removeListener(listener);
        }
    }

    private void tryToSendAgainLater() {
        if (resendCounter >= MAX_RESEND_ATTEMPTS) {
            cleanup();
            failed("We never received an ACK message when sending the msg to the peer. " +
                    "We fail here and do not publish the deposit tx.");
            return;
        }

        log.info("We send the message again to the peer after a delay of {} sec.", delayInSec);
        if (timer != null) {
            timer.stop();
        }
        timer = UserThread.runAfter(this::run, delayInSec, TimeUnit.SECONDS);

        if (resendCounter == 0) {
            // We want to register listener only once
            listener = (observable, oldValue, newValue) -> onMessageStateChange(newValue);
            processModel.getDepositTxMessageStateProperty().addListener(listener);
            onMessageStateChange(processModel.getDepositTxMessageStateProperty().get());
        }

        delayInSec = delayInSec * 2;
        resendCounter++;
    }

    private void onMessageStateChange(MessageState newValue) {
        // Once we receive an ACK from our msg we know the peer has received the msg and we stop.
        if (newValue == MessageState.ACKNOWLEDGED) {
            // We treat a ACK like SELLER_SAW_ARRIVED_DEPOSIT_TX_PUBLISHED_MSG
            // we no longer set deprecated state (Trade.State.SELLER_SAW_ARRIVED_DEPOSIT_TX_PUBLISHED_MSG);
            // see https://github.com/bisq-network/bisq/pull/5746#issuecomment-939879623

            cleanup();
            complete();
        }
    }
}

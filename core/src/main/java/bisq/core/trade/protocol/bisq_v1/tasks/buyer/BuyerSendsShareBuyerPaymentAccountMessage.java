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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.network.MessageState;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.messages.TradeMailboxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.SendMailboxMessageTask;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import javafx.beans.value.ChangeListener;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerSendsShareBuyerPaymentAccountMessage extends SendMailboxMessageTask {
    private static final int MAX_RESEND_ATTEMPTS = 7;
    private int delayInSec = 4;
    private int resendCounter = 0;
    private ShareBuyerPaymentAccountMessage message;
    private ChangeListener<MessageState> listener;
    private Timer timer;

    public BuyerSendsShareBuyerPaymentAccountMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected TradeMailboxMessage getTradeMailboxMessage(String tradeId) {
        if (message == null) {
            String deterministicId = tradeId + processModel.getMyNodeAddress().getFullAddress();
            PaymentAccountPayload buyerPaymentAccountPayload = processModel.getPaymentAccountPayload(trade);
            message = new ShareBuyerPaymentAccountMessage(
                    deterministicId,
                    processModel.getOfferId(),
                    processModel.getMyNodeAddress(),
                    buyerPaymentAccountPayload);
        }
        return message;
    }

    @Override
    protected void setStateSent() {
    }

    @Override
    protected void setStateArrived() {
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
            processModel.getTradeManager().requestPersistence();
            cleanup();
            complete();
        }
    }
}

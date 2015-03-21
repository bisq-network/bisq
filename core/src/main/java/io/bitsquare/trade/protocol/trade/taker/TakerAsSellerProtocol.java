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

package io.bitsquare.trade.protocol.trade.taker;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestTakerDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.taker.models.TakerAsSellerModel;
import io.bitsquare.trade.protocol.trade.taker.tasks.BroadcastTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessRequestTakerDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendPayoutTxToOfferer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendSignedTakerDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.SignAndPublishPayoutTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCommitDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCreatesAndSignsDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOffererAccount;
import io.bitsquare.util.Utilities;

import java.util.function.Function;

import javafx.animation.AnimationTimer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class TakerAsSellerProtocol {
    private static final Logger log = LoggerFactory.getLogger(TakerAsSellerProtocol.class);
    private static final int TIMEOUT_DELAY = 10000;

    private final TakerAsSellerModel model;
    private final MessageHandler messageHandler;
    private AnimationTimer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TakerAsSellerProtocol(TakerAsSellerModel model) {
        log.debug("New SellerAsTakerProtocol " + this);
        this.model = model;
        messageHandler = this::handleMessage;
        model.messageService.addMessageHandler(messageHandler);


    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        log.debug("cleanup " + this);
        model.messageService.removeMessageHandler(messageHandler);
    }

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        log.debug("setMailboxMessage " + mailboxMessage);
        // Might be called twice, so check that its only processed once
        if (model.mailboxMessage == null) {
            model.mailboxMessage = mailboxMessage;
            if (mailboxMessage instanceof FiatTransferStartedMessage) {
                handleFiatTransferStartedMessage((FiatTransferStartedMessage) mailboxMessage);
            }
            else if (mailboxMessage instanceof DepositTxPublishedMessage) {
                handleDepositTxPublishedMessage((DepositTxPublishedMessage) mailboxMessage);
            }
        }
    }

    public void takeAvailableOffer() {
        TaskRunner<TakerAsSellerModel> taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at takeAvailableOffer completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                CreateTakeOfferFeeTx.class,
                BroadcastTakeOfferFeeTx.class,
                SendRequestDepositTxInputsMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleRequestTakerDepositPaymentMessage(RequestTakerDepositPaymentMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TaskRunner<TakerAsSellerModel> taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleTakerDepositPaymentRequestMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                ProcessRequestTakerDepositPaymentMessage.class,
                VerifyOffererAccount.class,
                CreateAndSignContract.class,
                TakerCreatesAndSignsDepositTx.class,
                SendSignedTakerDepositTx.class
        );
        taskRunner.run();
    }

    private void handleDepositTxPublishedMessage(DepositTxPublishedMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TaskRunner<TakerAsSellerModel> taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleDepositTxPublishedMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                ProcessDepositTxPublishedMessage.class,
                TakerCommitDepositTx.class
        );
        taskRunner.run();
    }

    private void handleFiatTransferStartedMessage(FiatTransferStartedMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TaskRunner<TakerAsSellerModel> taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleFiatTransferStartedMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(ProcessFiatTransferStartedMessage.class);
        taskRunner.run();
    }

    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    public void onFiatPaymentReceived() {
        TaskRunner<TakerAsSellerModel> taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleFiatReceivedUIEvent completed");

                    // we are done!
                    model.onComplete();
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                SignAndPublishPayoutTx.class,
                VerifyOfferFeePayment.class,
                SendPayoutTxToOfferer.class
        );
        taskRunner.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleMessage(Message message, Peer sender) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + sender);
        if (message instanceof TradeMessage) {
            TradeMessage tradeMessage = (TradeMessage) message;
            nonEmptyStringOf(tradeMessage.tradeId);

            if (tradeMessage.tradeId.equals(model.id)) {
                if (tradeMessage instanceof RequestTakerDepositPaymentMessage) {
                    handleRequestTakerDepositPaymentMessage((RequestTakerDepositPaymentMessage) tradeMessage);
                }
                else if (tradeMessage instanceof DepositTxPublishedMessage) {
                    handleDepositTxPublishedMessage((DepositTxPublishedMessage) tradeMessage);
                }
                else if (tradeMessage instanceof FiatTransferStartedMessage) {
                    handleFiatTransferStartedMessage((FiatTransferStartedMessage) tradeMessage);
                }
                else {
                    log.error("Incoming message not supported. " + tradeMessage);
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Timeout
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void startTimeout(Function<AnimationTimer, Void> callback) {
        stopTimeout();
        timeoutTimer = Utilities.setTimeout(TIMEOUT_DELAY, callback);
        timeoutTimer.start();
    }

    private void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

}

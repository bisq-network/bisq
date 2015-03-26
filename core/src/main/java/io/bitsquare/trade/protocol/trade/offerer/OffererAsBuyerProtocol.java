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

package io.bitsquare.trade.protocol.trade.offerer;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.protocol.Protocol;
import io.bitsquare.trade.protocol.availability.messages.ReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.messages.RequestIsOfferAvailableMessage;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.offerer.models.OffererAsBuyerModel;
import io.bitsquare.trade.protocol.trade.offerer.tasks.CreateAndSignPayoutTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.CreateOffererDepositTxInputs;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessRequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.RequestTakerDepositPayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendBankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendDepositTxToTaker;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakerAccount;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.*;

public class OffererAsBuyerProtocol implements Protocol {
    private static final Logger log = LoggerFactory.getLogger(OffererAsBuyerProtocol.class);

    private final OffererAsBuyerModel model;
    private final MessageHandler messageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OffererAsBuyerProtocol(OffererAsBuyerModel model) {
        log.debug("New BuyerAsOffererProtocol " + this);
        this.model = model;
        messageHandler = this::handleMessage;

        model.messageService.addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        log.debug("setMailboxMessage " + mailboxMessage);
        // Might be called twice, so check that its only processed once
        if (model.mailboxMessage == null) {
            model.mailboxMessage = mailboxMessage;
            if (mailboxMessage instanceof PayoutTxPublishedMessage) {
                handlePayoutTxPublishedMessage((PayoutTxPublishedMessage) mailboxMessage);
            }
        }
    }

    public void cleanup() {
        log.debug("cleanup " + this);

        // tradeMessageService and transactionConfidence use CopyOnWriteArrayList as listeners, but be safe and delay remove a bit.
        Platform.runLater(() -> {
            model.messageService.removeMessageHandler(messageHandler);
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    // OpenOffer requests
    private void handleRequestIsOfferAvailableMessage(RequestIsOfferAvailableMessage tradeMessage, Peer sender) {
        try {
            checkTradeId(model.id, tradeMessage);

            // We don't store anything in the model as we might be in a trade process and receive that request from another peer who wants to take the offer
            // at the same time
            boolean isOfferOpen = model.trade.lifeCycleStateProperty().get() == OffererTrade.OffererLifeCycleState.OPEN_OFFER;
            ReportOfferAvailabilityMessage reportOfferAvailabilityMessage = new ReportOfferAvailabilityMessage(model.id, isOfferOpen);
            model.messageService.sendMessage(sender, reportOfferAvailabilityMessage, new SendMessageListener() {
                @Override
                public void handleResult() {
                    // Offerer does not do anything at that moment. Peer might only watch the offer and does not start a trade.
                    log.trace("ReportOfferAvailabilityMessage successfully arrived at peer");
                }

                @Override
                public void handleFault() {
                    log.warn("Sending ReportOfferAvailabilityMessage failed.");
                }
            });
        } catch (Throwable t) {
            // We don't handle the error as we might be in a trade process with another trader
            t.printStackTrace();
            log.warn("Exception at handleRequestIsOfferAvailableMessage " + t.getMessage());
        }
    }

    // Trade started
    private void handleRequestDepositTxInputsMessage(RequestDepositTxInputsMessage tradeMessage, Peer taker) {
        checkTradeId(model.id, tradeMessage);
        model.setTradeMessage(tradeMessage);
        model.trade.setTradingPeer(taker);

        TaskRunner<OffererAsBuyerModel> taskRunner = new TaskRunner<>(model,
                () -> log.debug("taskRunner at handleTakeOfferFeePayedMessage completed"),
                (errorMessage) -> handleTaskRunnerFault(errorMessage));
        taskRunner.addTasks(
                ProcessRequestDepositTxInputsMessage.class,
                CreateOffererDepositTxInputs.class,
                RequestTakerDepositPayment.class
        );
        taskRunner.run();
    }

    private void handleRequestOffererPublishDepositTxMessage(RequestOffererPublishDepositTxMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TaskRunner<OffererAsBuyerModel> taskRunner = new TaskRunner<>(model,
                () -> log.debug("taskRunner at handleRequestOffererPublishDepositTxMessage completed"),
                (errorMessage) -> handleTaskRunnerFault(errorMessage));
        taskRunner.addTasks(
                ProcessRequestOffererPublishDepositTxMessage.class,
                VerifyTakerAccount.class,
                VerifyAndSignContract.class,
                SignAndPublishDepositTx.class,
                SendDepositTxToTaker.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    public void onFiatPaymentStarted() {
        TaskRunner<OffererAsBuyerModel> taskRunner = new TaskRunner<>(model,
                () -> log.debug("taskRunner at handleBankTransferStartedUIEvent completed"),
                (errorMessage) -> handleTaskRunnerFault(errorMessage));
        taskRunner.addTasks(
                CreateAndSignPayoutTx.class,
                VerifyTakeOfferFeePayment.class,
                SendBankTransferStartedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handlePayoutTxPublishedMessage(PayoutTxPublishedMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TaskRunner<OffererAsBuyerModel> taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handlePayoutTxPublishedMessage completed");
                    // we are done!
                    model.onComplete();
                },
                (errorMessage) -> handleTaskRunnerFault(errorMessage));

        taskRunner.addTasks(ProcessPayoutTxPublishedMessage.class);
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
                if (tradeMessage instanceof RequestIsOfferAvailableMessage) {
                    handleRequestIsOfferAvailableMessage((RequestIsOfferAvailableMessage) tradeMessage, sender);
                }
                else if (tradeMessage instanceof RequestDepositTxInputsMessage) {
                    handleRequestDepositTxInputsMessage((RequestDepositTxInputsMessage) tradeMessage, sender);
                }
                else if (tradeMessage instanceof RequestOffererPublishDepositTxMessage) {
                    handleRequestOffererPublishDepositTxMessage((RequestOffererPublishDepositTxMessage) tradeMessage);
                }
                else if (tradeMessage instanceof PayoutTxPublishedMessage) {
                    handlePayoutTxPublishedMessage((PayoutTxPublishedMessage) tradeMessage);
                }
                else {
                    log.error("Incoming tradeMessage not supported. " + tradeMessage);
                }
            }
        }
    }

    private void handleTaskRunnerFault(String errorMessage) {
        cleanup();
    }
}

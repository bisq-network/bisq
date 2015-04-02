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

package io.bitsquare.trade.protocol.trade.buyer;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.availability.messages.ReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.messages.RequestIsOfferAvailableMessage;
import io.bitsquare.trade.protocol.trade.TradeProtocol;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerCommitsPayoutTx;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerCreatesAndSignPayoutTx;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerCreatesDepositTxInputs;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerProcessPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerProcessRequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerSendsDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerSendsFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerSendsRequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerSignsAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerVerifiesAndSignsContract;
import io.bitsquare.trade.protocol.trade.buyer.tasks.ProcessRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.shared.models.ProcessModel;
import io.bitsquare.trade.protocol.trade.shared.offerer.tasks.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.shared.offerer.tasks.VerifyTakerAccount;
import io.bitsquare.trade.states.OffererState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.*;

public class BuyerAsOffererProtocol implements TradeProtocol {
    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererProtocol.class);

    private MessageHandler messageHandler;
    private final BuyerAsOffererTrade trade;
    private final ProcessModel processModel;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsOffererProtocol(BuyerAsOffererTrade trade) {
        log.debug("New OffererProtocol " + this);
        this.trade = trade;
        processModel = this.trade.getProcessModel();
        messageHandler = this::handleMessage;

        processModel.getMessageService().addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        log.debug("setMailboxMessage " + mailboxMessage);
        // Might be called twice, so check that its only processed once
        if (processModel.getMailboxMessage() == null) {
            processModel.setMailboxMessage(mailboxMessage);
            if (mailboxMessage instanceof PayoutTxPublishedMessage) {
                handlePayoutTxPublishedMessage((PayoutTxPublishedMessage) mailboxMessage);
            }
        }
    }

    public void cleanup() {
        log.debug("cleanup " + this);

        if (messageHandler != null) {
            processModel.getMessageService().removeMessageHandler(messageHandler);
            messageHandler = null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    // OpenOffer requests
    private void handleRequestIsOfferAvailableMessage(RequestIsOfferAvailableMessage tradeMessage, Peer sender) {
        try {
            checkTradeId(processModel.getId(), tradeMessage);

            // We don't store anything in the offererTradeProcessModel as we might be in a trade process and receive that request from another peer who wants
            // to take the
            // offer
            // at the same time
            boolean isOfferOpen = trade.lifeCycleStateProperty().get() == OffererState.LifeCycleState.OFFER_OPEN;
            ReportOfferAvailabilityMessage reportOfferAvailabilityMessage = new ReportOfferAvailabilityMessage(processModel.getId(), isOfferOpen);
            processModel.getMessageService().sendMessage(sender, reportOfferAvailabilityMessage, new SendMessageListener() {
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

    // Trade started. We reserve the offer for that taker. If anything goes wrong we reset the offer as open.
    private void handleRequestDepositTxInputsMessage(RequestDepositTxInputsMessage tradeMessage, Peer taker) {
        checkTradeId(processModel.getId(), tradeMessage);
        processModel.setTradeMessage(tradeMessage);
        trade.setTradingPeer(taker);

        trade.setLifeCycleState(OffererState.LifeCycleState.OFFER_RESERVED);

        TaskRunner<Trade> taskRunner = new TaskRunner<>(trade,
                () -> log.debug("taskRunner at handleRequestDepositTxInputsMessage completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                ProcessRequestDepositTxInputsMessage.class,
                BuyerCreatesDepositTxInputs.class,
                BuyerSendsRequestPayDepositMessage.class
        );
        taskRunner.run();
    }

    private void handleRequestPublishDepositTxMessage(RequestPublishDepositTxMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<Trade> taskRunner = new TaskRunner<>(trade,
                () -> log.debug("taskRunner at handleRequestPublishDepositTxMessage completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                BuyerProcessRequestPublishDepositTxMessage.class,
                VerifyTakerAccount.class,
                BuyerVerifiesAndSignsContract.class,
                BuyerSignsAndPublishDepositTx.class,
                BuyerSendsDepositTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    public void onFiatPaymentStarted() {
        TaskRunner<Trade> taskRunner = new TaskRunner<>(trade,
                () -> log.debug("taskRunner at handleBankTransferStartedUIEvent completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                VerifyTakeOfferFeePayment.class,
                BuyerCreatesAndSignPayoutTx.class,
                BuyerSendsFiatTransferStartedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handlePayoutTxPublishedMessage(PayoutTxPublishedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<Trade> taskRunner = new TaskRunner<>(trade,
                () -> {
                    log.debug("taskRunner at handlePayoutTxPublishedMessage completed");
                    // we are done!
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(BuyerProcessPayoutTxPublishedMessage.class);
        taskRunner.addTasks(BuyerCommitsPayoutTx.class);
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

            if (tradeMessage.tradeId.equals(trade.getId())) {
                if (tradeMessage instanceof RequestIsOfferAvailableMessage) {
                    handleRequestIsOfferAvailableMessage((RequestIsOfferAvailableMessage) tradeMessage, sender);
                }
                else if (tradeMessage instanceof RequestDepositTxInputsMessage) {
                    handleRequestDepositTxInputsMessage((RequestDepositTxInputsMessage) tradeMessage, sender);
                }
                else if (tradeMessage instanceof RequestPublishDepositTxMessage) {
                    handleRequestPublishDepositTxMessage((RequestPublishDepositTxMessage) tradeMessage);
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
        log.error(errorMessage);
        cleanup();
    }
}

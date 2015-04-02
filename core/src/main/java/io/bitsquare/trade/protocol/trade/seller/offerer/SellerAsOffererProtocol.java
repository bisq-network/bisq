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

package io.bitsquare.trade.protocol.trade.seller.offerer;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.SellerAsOffererTrade;
import io.bitsquare.trade.protocol.availability.messages.ReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.messages.RequestIsOfferAvailableMessage;
import io.bitsquare.trade.protocol.trade.TradeProtocol;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererCommitDepositTx;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererCreatesAndSignsContract;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererCreatesAndSignsDepositTx;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererProcessFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererProcessRequestPayDepositFromOffererMessage;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererSendsPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererSendsRequestPublishDepositTxFromTakerMessage;
import io.bitsquare.trade.protocol.trade.seller.offerer.tasks.OffererSignsAndPublishPayoutTx;
import io.bitsquare.trade.protocol.trade.shared.models.ProcessModel;
import io.bitsquare.trade.protocol.trade.shared.offerer.tasks.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.shared.offerer.tasks.VerifyTakerAccount;
import io.bitsquare.trade.states.OffererState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.*;

public class SellerAsOffererProtocol implements TradeProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerAsOffererProtocol.class);

    private final MessageHandler messageHandler;
    private final SellerAsOffererTrade sellerAsOffererTrade;
    private final ProcessModel processModel;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsOffererProtocol(SellerAsOffererTrade model) {
        log.debug("New OffererProtocol " + this);
        this.sellerAsOffererTrade = model;
        processModel = sellerAsOffererTrade.getProcessModel();
        messageHandler = this::handleMessage;

        processModel.getMessageService().addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        log.debug("setMailboxMessage " + mailboxMessage);
        // Might be called twice, so check that its only processed once
       /* if (offererTradeProcessModel.getMailboxMessage() == null) {
            offererTradeProcessModel.setMailboxMessage(mailboxMessage);
            if (mailboxMessage instanceof PayoutTxPublishedMessage) {
                handlePayoutTxPublishedMessage((PayoutTxPublishedMessage) mailboxMessage);
            }
        }*/
    }

    public void cleanup() {
        log.debug("cleanup " + this);

        processModel.getMessageService().removeMessageHandler(messageHandler);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling 
    ///////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////
    // IsOfferAvailable
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(RequestIsOfferAvailableMessage tradeMessage, Peer sender) {
        try {
            checkTradeId(processModel.getId(), tradeMessage);

            // We don't store anything in the offererTradeProcessModel as we might be in a trade process and receive that request from another peer who wants
            // to take the
            // offer
            // at the same time
            boolean isOfferOpen = sellerAsOffererTrade.lifeCycleStateProperty().get() == OffererState.LifeCycleState.OFFER_OPEN;

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(RequestPayDepositMessage tradeMessage, Peer sender) {
        processModel.setTradeMessage(tradeMessage);

        sellerAsOffererTrade.setTradingPeer(sender);

        TaskRunner<SellerAsOffererTrade> taskRunner = new TaskRunner<>(sellerAsOffererTrade,
                () -> log.debug("taskRunner at handleTakerDepositPaymentRequestMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                OffererProcessRequestPayDepositFromOffererMessage.class,
                VerifyTakerAccount.class,
                OffererCreatesAndSignsContract.class,
                OffererCreatesAndSignsDepositTx.class,
                OffererSendsRequestPublishDepositTxFromTakerMessage.class
        );
        taskRunner.run();
    }

    private void handle(DepositTxPublishedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<SellerAsOffererTrade> taskRunner = new TaskRunner<>(sellerAsOffererTrade,
                () -> log.debug("taskRunner at handleDepositTxPublishedMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                OffererProcessDepositTxPublishedMessage.class,
                OffererCommitDepositTx.class
        );
        taskRunner.run();
    }

    private void handle(FiatTransferStartedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<SellerAsOffererTrade> taskRunner = new TaskRunner<>(sellerAsOffererTrade,
                () -> log.debug("taskRunner at handleFiatTransferStartedMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(OffererProcessFiatTransferStartedMessage.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    public void onFiatPaymentReceived() {
        sellerAsOffererTrade.setProcessState(OffererState.ProcessState.FIAT_PAYMENT_RECEIVED);

        TaskRunner<SellerAsOffererTrade> taskRunner = new TaskRunner<>(sellerAsOffererTrade,
                () -> {
                    log.debug("taskRunner at handleFiatReceivedUIEvent completed");

                    // we are done!
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                VerifyTakeOfferFeePayment.class,
                OffererSignsAndPublishPayoutTx.class,
                OffererSendsPayoutTxPublishedMessage.class
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
            if (tradeMessage.tradeId.equals(sellerAsOffererTrade.getId())) {
                if (tradeMessage instanceof RequestIsOfferAvailableMessage) {
                    handle((RequestIsOfferAvailableMessage) tradeMessage, sender);
                }
                else if (tradeMessage instanceof RequestPayDepositMessage) {
                    handle((RequestPayDepositMessage) tradeMessage, sender);
                }
                else if (tradeMessage instanceof DepositTxPublishedMessage) {
                    handle((DepositTxPublishedMessage) tradeMessage);
                }
                else if (tradeMessage instanceof FiatTransferStartedMessage) {
                    handle((FiatTransferStartedMessage) tradeMessage);
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

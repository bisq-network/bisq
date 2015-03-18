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

import io.bitsquare.network.Message;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.handlers.MessageHandler;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.offerer.models.BuyerAsOffererModel;
import io.bitsquare.trade.protocol.trade.offerer.tasks.CreateOffererDepositTxInputs;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessRequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.RequestDepositPayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendBankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendDepositTxIdToTaker;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SetupListenerForBlockChainConfirmation;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SignPayoutTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakerAccount;

import org.bitcoinj.core.TransactionConfidence;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.*;

public class BuyerAsOffererProtocol {
    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererProtocol.class);

    private final BuyerAsOffererModel model;
    private final MessageHandler messageHandler;

    private TransactionConfidence.Listener transactionConfidenceListener;
    private TransactionConfidence transactionConfidence;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsOffererProtocol(BuyerAsOffererModel model) {
        log.debug("New BuyerAsOffererProtocol " + this);
        this.model = model;
        messageHandler = this::handleMessage;

        model.tradeMessageService.addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        log.debug("cleanup " + this);
        
        // tradeMessageService and transactionConfidence use CopyOnWriteArrayList as listeners, but be safe and delay remove a bit.
        Platform.runLater(() -> {
            model.tradeMessageService.removeMessageHandler(messageHandler);
            
            if (transactionConfidence != null) {
                if (!transactionConfidence.removeEventListener(transactionConfidenceListener))
                    throw new RuntimeException("Remove transactionConfidenceListener failed at BuyerAsOffererProtocol.");
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleRequestDepositTxInputsMessage(RequestDepositTxInputsMessage tradeMessage, Peer taker) {
        checkTradeId(model.id, tradeMessage);
        model.setTradeMessage(tradeMessage);
        model.taker.peer = taker;

        BuyerAsOffererTaskRunner<BuyerAsOffererModel> taskRunner = new BuyerAsOffererTaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleTakeOfferFeePayedMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                ProcessRequestDepositTxInputsMessage.class,
                CreateOffererDepositTxInputs.class,
                RequestDepositPayment.class
        );
        taskRunner.run();
    }

    private void handleRequestOffererPublishDepositTxMessage(RequestOffererPublishDepositTxMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        BuyerAsOffererTaskRunner<BuyerAsOffererModel> taskRunner = new BuyerAsOffererTaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleRequestOffererPublishDepositTxMessage completed");
                    transactionConfidenceListener = (tx, reason) -> {
                        log.trace("onConfidenceChanged " + tx.getConfidence());
                        if (reason == TransactionConfidence.Listener.ChangeReason.TYPE && tx.getConfidence().getConfidenceType() == TransactionConfidence
                                .ConfidenceType.BUILDING) {

                            model.trade.setState(Trade.State.DEPOSIT_CONFIRMED);
                        }
                    };
                    transactionConfidence = model.trade.getDepositTx().getConfidence();
                    transactionConfidence.addEventListener(transactionConfidenceListener);
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                ProcessRequestOffererPublishDepositTxMessage.class,
                VerifyTakerAccount.class,
                VerifyAndSignContract.class,
                SignAndPublishDepositTx.class,
                SetupListenerForBlockChainConfirmation.class,
                SendDepositTxIdToTaker.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    public void onFiatPaymentStarted() {
        BuyerAsOffererTaskRunner<BuyerAsOffererModel> taskRunner = new BuyerAsOffererTaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleBankTransferStartedUIEvent completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                SignPayoutTx.class,
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

        BuyerAsOffererTaskRunner<BuyerAsOffererModel> taskRunner = new BuyerAsOffererTaskRunner<>(model,
                () -> {
                    log.debug("sequence at handlePayoutTxPublishedMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
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
                if (tradeMessage instanceof RequestDepositTxInputsMessage) {
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
}

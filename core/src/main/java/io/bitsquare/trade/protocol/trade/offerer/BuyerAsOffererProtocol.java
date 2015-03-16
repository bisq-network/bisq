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
import io.bitsquare.trade.protocol.trade.TradeMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.GetOffererDepositTxInputs;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessRequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessRequestTakeOfferMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessTakeOfferFeePayedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.RequestDepositPayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.RespondToTakeOfferRequest;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendBankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendDepositTxIdToTaker;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SetupListenerForBlockChainConfirmation;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SignPayoutTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.taker.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestTakeOfferMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.TakeOfferFeePayedMessage;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class BuyerAsOffererProtocol {

    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererProtocol.class);

    private final BuyerAsOffererModel model;
    private final MessageHandler messageHandler;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsOffererProtocol(BuyerAsOffererModel model) {
        this.model = model;
        messageHandler = this::handleMessage;

        model.getTradeMessageService().addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        model.getTradeMessageService().removeMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleRequestTakeOfferMessage(RequestTakeOfferMessage tradeMessage, Peer peer) {
        model.setTradeMessage(tradeMessage);
        model.setTaker(peer);

        BuyerAsOffererTaskRunner<BuyerAsOffererModel> taskRunner = new BuyerAsOffererTaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleRequestTakeOfferMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                ProcessRequestTakeOfferMessage.class,
                RespondToTakeOfferRequest.class
        );
        taskRunner.run();
    }

    private void handleTakeOfferFeePayedMessage(TakeOfferFeePayedMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        BuyerAsOffererTaskRunner<BuyerAsOffererModel> taskRunner = new BuyerAsOffererTaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleTakeOfferFeePayedMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                ProcessTakeOfferFeePayedMessage.class,
                GetOffererDepositTxInputs.class,
                RequestDepositPayment.class
        );
        taskRunner.run();
    }

    private void handleRequestOffererPublishDepositTxMessage(RequestOffererPublishDepositTxMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        BuyerAsOffererTaskRunner<BuyerAsOffererModel> taskRunner = new BuyerAsOffererTaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleRequestOffererPublishDepositTxMessage completed");
                    TransactionConfidence confidence = model.getTrade().getDepositTx().getConfidence();
                    confidence.addEventListener(new TransactionConfidence.Listener() {
                        @Override
                        public void onConfidenceChanged(Transaction tx, ChangeReason reason) {
                            log.trace("onConfidenceChanged " + tx.getConfidence());
                            if (reason == ChangeReason.TYPE && tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {

                                model.getTrade().setState(Trade.State.DEPOSIT_CONFIRMED);

                                //TODO not sure if that works
                                Platform.runLater(() -> confidence.removeEventListener(this));
                            }
                        }
                    });
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

    private void handleMessage(Message message, Peer peer) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName());
        if (message instanceof TradeMessage) {
            TradeMessage tradeMessage = (TradeMessage) message;
            nonEmptyStringOf(tradeMessage.getTradeId());

            if (tradeMessage.getTradeId().equals(model.getOffer().getId())) {
                if (tradeMessage instanceof RequestTakeOfferMessage) {
                    handleRequestTakeOfferMessage((RequestTakeOfferMessage) tradeMessage, peer);
                }
                else if (tradeMessage instanceof TakeOfferFeePayedMessage) {
                    handleTakeOfferFeePayedMessage((TakeOfferFeePayedMessage) tradeMessage);
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

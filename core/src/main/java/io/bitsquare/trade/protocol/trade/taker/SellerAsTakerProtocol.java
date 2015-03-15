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

import io.bitsquare.network.Message;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.handlers.MessageHandler;
import io.bitsquare.trade.protocol.trade.TradeMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RequestDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.taker.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCreatesAndSignsDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.PayTakeOfferFee;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessBankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessRequestDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessRespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.RequestTakeOffer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendPayoutTxToOfferer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendSignedTakerDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendTakeOfferFeePayedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.SignAndPublishPayoutTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCommitDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOffererAccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class SellerAsTakerProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerProtocol.class);

    private final SellerAsTakerModel model;
    private final MessageHandler messageHandler;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol(SellerAsTakerModel model) {
        this.model = model;
        messageHandler = this::handleMessage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        model.getTradeMessageService().removeMessageHandler(messageHandler);
    }

    public void takeOffer() {
        model.getTradeMessageService().addMessageHandler(messageHandler);

        SellerAsTakerTaskRunner<SellerAsTakerModel> taskRunner = new SellerAsTakerTaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleRequestTakeOfferUIEvent completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                GetPeerAddress.class,
                RequestTakeOffer.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleRespondToTakeOfferRequestMessage(RespondToTakeOfferRequestMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        SellerAsTakerTaskRunner<SellerAsTakerModel> taskRunner = new SellerAsTakerTaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleRespondToTakeOfferRequestMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                ProcessRespondToTakeOfferRequestMessage.class,
                PayTakeOfferFee.class,
                SendTakeOfferFeePayedMessage.class
        );
        taskRunner.run();
    }

    private void handleRequestDepositPaymentMessage(RequestDepositPaymentMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        SellerAsTakerTaskRunner<SellerAsTakerModel> taskRunner = new SellerAsTakerTaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleTakerDepositPaymentRequestMessage completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                ProcessRequestDepositPaymentMessage.class,
                VerifyOffererAccount.class,
                CreateAndSignContract.class,
                TakerCreatesAndSignsDepositTx.class,
                SendSignedTakerDepositTx.class
        );
        taskRunner.run();
    }

    private void handleDepositTxPublishedMessage(DepositTxPublishedMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        SellerAsTakerTaskRunner<SellerAsTakerModel> taskRunner = new SellerAsTakerTaskRunner<>(model,
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

    private void handleBankTransferStartedMessage(BankTransferStartedMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        SellerAsTakerTaskRunner<SellerAsTakerModel> taskRunner = new SellerAsTakerTaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleBankTransferInitedMessage completed");
                    model.getTrade().setState(Trade.State.FIAT_PAYMENT_STARTED);
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(ProcessBankTransferStartedMessage.class);
        taskRunner.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    public void onFiatPaymentReceived() {
        SellerAsTakerTaskRunner<SellerAsTakerModel> taskRunner = new SellerAsTakerTaskRunner<>(model,
                () -> {
                    log.debug("taskRunner at handleFiatReceivedUIEvent completed");
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
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName());
        if (message instanceof TradeMessage) {
            TradeMessage tradeMessage = (TradeMessage) message;
            nonEmptyStringOf(tradeMessage.getTradeId());
           
            if (tradeMessage.getTradeId().equals(model.getOffer().getId())) {
                if (tradeMessage instanceof RespondToTakeOfferRequestMessage) {
                    handleRespondToTakeOfferRequestMessage((RespondToTakeOfferRequestMessage) tradeMessage);
                }
                else if (tradeMessage instanceof RequestDepositPaymentMessage) {
                    handleRequestDepositPaymentMessage((RequestDepositPaymentMessage) tradeMessage);
                }
                else if (tradeMessage instanceof DepositTxPublishedMessage) {
                    handleDepositTxPublishedMessage((DepositTxPublishedMessage) tradeMessage);
                }
                else if (tradeMessage instanceof BankTransferStartedMessage) {
                    handleBankTransferStartedMessage((BankTransferStartedMessage) tradeMessage);
                }
                else {
                    log.error("Incoming message not supported. " + tradeMessage);
                }
            }
        }
    }
}

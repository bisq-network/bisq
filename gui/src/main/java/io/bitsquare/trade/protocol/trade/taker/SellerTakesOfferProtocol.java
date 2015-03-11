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
import io.bitsquare.trade.TradeTaskRunner;
import io.bitsquare.trade.protocol.trade.TradeMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferInitedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.TakerDepositPaymentRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.taker.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.trade.taker.tasks.PayDeposit;
import io.bitsquare.trade.protocol.trade.taker.tasks.PayTakeOfferFee;
import io.bitsquare.trade.protocol.trade.taker.tasks.RequestTakeOffer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendPayoutTxToOfferer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendSignedTakerDepositTxAsHex;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendTakeOfferFeePayedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.SignAndPublishPayoutTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCommitDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.ValidateBankTransferInitedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ValidateDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ValidateRespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ValidateTakerDepositPaymentRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOffererAccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;


/**
 * Responsible for the correct execution of the sequence of tasks, message passing to the peer and message processing
 * from the peer.
 * That class handles the role of the taker as the Bitcoin seller.
 * It uses sub tasks to not pollute the main class too much with all the async result/fault handling.
 * Any data from incoming messages as well data used to send to the peer need to be validated before further processing.
 */
public class SellerTakesOfferProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerTakesOfferProtocol.class);

    private final SellerTakesOfferModel model;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerTakesOfferProtocol(SellerTakesOfferModel model) {
        this.model = model;
    }

    public void start() {
        model.getTradeMessageService().addMessageHandler(this::handleMessage);

        TradeTaskRunner<SellerTakesOfferModel> sequence1 = new TradeTaskRunner<>(model,
                () -> {
                    log.debug("sequence1 completed");
                },
                (message, throwable) -> {
                    log.error(message);
                }
        );
        sequence1.addTasks(
                GetPeerAddress.class,
                RequestTakeOffer.class
        );
        sequence1.run();
    }

    public void cleanup() {
        model.getTradeMessageService().removeMessageHandler(this::handleMessage);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleMessage(Message message, Peer sender) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName());
        if (message instanceof TradeMessage) {
            TradeMessage tradeMessage = (TradeMessage) message;
            nonEmptyStringOf(tradeMessage.getTradeId());

            if (tradeMessage instanceof RespondToTakeOfferRequestMessage) {
                handleTradeMessage((RespondToTakeOfferRequestMessage) tradeMessage);
            }
            else if (tradeMessage instanceof TakerDepositPaymentRequestMessage) {
                handleTradeMessage((TakerDepositPaymentRequestMessage) tradeMessage);
            }
            else if (tradeMessage instanceof DepositTxPublishedMessage) {
                handleTradeMessage((DepositTxPublishedMessage) tradeMessage);
            }
            else if (tradeMessage instanceof BankTransferInitedMessage) {
                handleTradeMessage((BankTransferInitedMessage) tradeMessage);
            }
            else {
                log.error("Incoming message not supported. " + tradeMessage);
            }
        }
    }

    private void handleTradeMessage(RespondToTakeOfferRequestMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TradeTaskRunner<SellerTakesOfferModel> sequence2 = new TradeTaskRunner<>(model,
                () -> {
                    log.debug("sequence2 completed");
                },
                (message, throwable) -> {
                    log.error(message);
                }
        );
        sequence2.addTasks(
                ValidateRespondToTakeOfferRequestMessage.class,
                PayTakeOfferFee.class,
                SendTakeOfferFeePayedMessage.class
        );
        sequence2.run();
    }

    private void handleTradeMessage(TakerDepositPaymentRequestMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TradeTaskRunner<SellerTakesOfferModel> sequence3 = new TradeTaskRunner<>(model,
                () -> {
                    log.debug("sequence3 completed");
                },
                (message, throwable) -> {
                    log.error(message);
                }
        );
        sequence3.addTasks(
                ValidateTakerDepositPaymentRequestMessage.class,
                VerifyOffererAccount.class,
                CreateAndSignContract.class,
                PayDeposit.class,
                SendSignedTakerDepositTxAsHex.class
        );
        sequence3.run();
    }

    private void handleTradeMessage(DepositTxPublishedMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TradeTaskRunner<SellerTakesOfferModel> sequence4 = new TradeTaskRunner<>(model,
                () -> {
                    log.debug("sequence4 completed");
                },
                (message, throwable) -> {
                    log.error(message);
                }
        );
        sequence4.addTasks(
                ValidateDepositTxPublishedMessage.class,
                TakerCommitDepositTx.class
        );
        sequence4.run();
    }

    private void handleTradeMessage(BankTransferInitedMessage tradeMessage) {
        model.setTradeMessage(tradeMessage);

        TradeTaskRunner<SellerTakesOfferModel> sequence5 = new TradeTaskRunner<>(model,
                () -> {
                    log.debug("sequence5 completed");
                    model.getTrade().setState(Trade.State.FIAT_PAYMENT_STARTED);
                },
                (message, throwable) -> {
                    log.error(message);
                }
        );
        sequence5.addTasks(ValidateBankTransferInitedMessage.class);
        sequence5.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI event handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    public void handleFiatReceivedUIEvent() {
        TradeTaskRunner<SellerTakesOfferModel> sequence6 = new TradeTaskRunner<>(model,
                () -> {
                    log.debug("sequence6 completed");
                },
                (message, throwable) -> {
                    log.error(message);
                }
        );
        sequence6.addTasks(
                SignAndPublishPayoutTx.class,
                VerifyOfferFeePayment.class,
                SendPayoutTxToOfferer.class
        );
        sequence6.run();
    }
}

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
import io.bitsquare.trade.TakerAsBuyerTrade;
import io.bitsquare.trade.protocol.Protocol;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.taker.models.TakerProcessModel;
import io.bitsquare.trade.protocol.trade.taker.tasks.BroadcastTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCommitsPayoutTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCreatesAndSignsPayoutTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCreatesDepositTxInputs;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerProcessPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerProcessRequestPublishDepositTxFromTakerMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerSendsDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerSendsFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerSendsRequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerSignsAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerVerifiesAndSignsContract;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOffererAccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class TakerAsBuyerProtocol implements Protocol {
    private static final Logger log = LoggerFactory.getLogger(TakerAsBuyerProtocol.class);

    private final TakerAsBuyerTrade takerAsBuyerTrade;
    private final TakerProcessModel takerTradeProcessModel;
    private final MessageHandler messageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TakerAsBuyerProtocol(TakerAsBuyerTrade takerTrade) {
        log.debug("New SellerAsTakerProtocol " + this);
        this.takerAsBuyerTrade = takerTrade;
        takerTradeProcessModel = takerTrade.getProcessModel();

        messageHandler = this::handleMessage;
        takerTradeProcessModel.getMessageService().addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        log.debug("cleanup " + this);
        takerTradeProcessModel.getMessageService().removeMessageHandler(messageHandler);
    }

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        log.debug("setMailboxMessage " + mailboxMessage);
        // Might be called twice, so check that its only processed once
       /* if (takerTradeProcessModel.getMailboxMessage() == null) {
            takerTradeProcessModel.setMailboxMessage(mailboxMessage);
            if (mailboxMessage instanceof FiatTransferStartedMessage) {
                handleFiatTransferStartedMessage((FiatTransferStartedMessage) mailboxMessage);
            }
            else if (mailboxMessage instanceof DepositTxPublishedMessage) {
                handleDepositTxPublishedMessage((DepositTxPublishedMessage) mailboxMessage);
            }
        }*/
    }

    public void takeAvailableOffer() {
        TaskRunner<TakerAsBuyerTrade> taskRunner = new TaskRunner<>(takerAsBuyerTrade,
                () -> log.debug("taskRunner at takeAvailableOffer completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                CreateTakeOfferFeeTx.class,
                BroadcastTakeOfferFeeTx.class,
                TakerCreatesDepositTxInputs.class,
                TakerSendsRequestPayDepositMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(RequestPublishDepositTxMessage tradeMessage) {
        takerTradeProcessModel.setTradeMessage(tradeMessage);

        TaskRunner<TakerAsBuyerTrade> taskRunner = new TaskRunner<>(takerAsBuyerTrade,
                () -> log.debug("taskRunner at handleRequestPublishDepositTxMessage completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                TakerProcessRequestPublishDepositTxFromTakerMessage.class,
                VerifyOffererAccount.class,
                TakerVerifiesAndSignsContract.class,
                TakerSignsAndPublishDepositTx.class,
                TakerSendsDepositTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    public void onFiatPaymentStarted() {
        TaskRunner<TakerAsBuyerTrade> taskRunner = new TaskRunner<>(takerAsBuyerTrade,
                () -> log.debug("taskRunner at onFiatPaymentStarted completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                VerifyOfferFeePayment.class,
                TakerCreatesAndSignsPayoutTx.class,
                TakerSendsFiatTransferStartedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PayoutTxPublishedMessage tradeMessage) {
        takerTradeProcessModel.setTradeMessage(tradeMessage);

        TaskRunner<TakerAsBuyerTrade> taskRunner = new TaskRunner<>(takerAsBuyerTrade,
                () -> {
                    log.debug("taskRunner at handlePayoutTxPublishedMessage completed");
                    // we are done!
                    takerTradeProcessModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                TakerProcessPayoutTxPublishedMessage.class,
                TakerCommitsPayoutTx.class);
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

            if (tradeMessage.tradeId.equals(takerTradeProcessModel.getId())) {
                if (tradeMessage instanceof RequestPublishDepositTxMessage) {
                    handle((RequestPublishDepositTxMessage) tradeMessage);
                }
                else if (tradeMessage instanceof PayoutTxPublishedMessage) {
                    handle((PayoutTxPublishedMessage) tradeMessage);
                }
                else {
                    log.error("Incoming message not supported. " + tradeMessage);
                }
            }
        }
    }

    private void handleTaskRunnerFault(String errorMessage) {
        log.error(errorMessage);
        cleanup();
    }

}

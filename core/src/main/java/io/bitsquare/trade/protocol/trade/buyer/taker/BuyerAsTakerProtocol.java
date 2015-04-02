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

package io.bitsquare.trade.protocol.trade.buyer.taker;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.BuyerAsTakerTrade;
import io.bitsquare.trade.protocol.trade.TradeProtocol;
import io.bitsquare.trade.protocol.trade.buyer.taker.tasks.TakerProcessRequestPublishDepositTxFromSellerMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerCommitsPayoutTx;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerCreatesAndSignPayoutTx;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerCreatesDepositTxInputs;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerProcessPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerSendsDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerSendsFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerSendsRequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerSignsAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.buyer.tasks.BuyerVerifiesAndSignsContract;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.shared.models.ProcessModel;
import io.bitsquare.trade.protocol.trade.shared.taker.tasks.BroadcastTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.shared.taker.tasks.CreateTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.shared.taker.tasks.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.shared.taker.tasks.VerifyOffererAccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class BuyerAsTakerProtocol implements TradeProtocol {
    private static final Logger log = LoggerFactory.getLogger(BuyerAsTakerProtocol.class);

    private final BuyerAsTakerTrade buyerAsTakerTrade;
    private final ProcessModel processModel;
    private final MessageHandler messageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsTakerProtocol(BuyerAsTakerTrade trade) {
        log.debug("New SellerAsTakerProtocol " + this);
        this.buyerAsTakerTrade = trade;
        processModel = trade.getProcessModel();

        messageHandler = this::handleMessage;
        processModel.getMessageService().addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        log.debug("cleanup " + this);
        processModel.getMessageService().removeMessageHandler(messageHandler);
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
        TaskRunner<BuyerAsTakerTrade> taskRunner = new TaskRunner<>(buyerAsTakerTrade,
                () -> log.debug("taskRunner at takeAvailableOffer completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                CreateTakeOfferFeeTx.class,
                BroadcastTakeOfferFeeTx.class,
                BuyerCreatesDepositTxInputs.class,
                BuyerSendsRequestPayDepositMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(RequestPublishDepositTxMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<BuyerAsTakerTrade> taskRunner = new TaskRunner<>(buyerAsTakerTrade,
                () -> log.debug("taskRunner at handleRequestPublishDepositTxMessage completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                TakerProcessRequestPublishDepositTxFromSellerMessage.class,
                VerifyOffererAccount.class,
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
        TaskRunner<BuyerAsTakerTrade> taskRunner = new TaskRunner<>(buyerAsTakerTrade,
                () -> log.debug("taskRunner at onFiatPaymentStarted completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                VerifyOfferFeePayment.class,
                BuyerCreatesAndSignPayoutTx.class,
                BuyerSendsFiatTransferStartedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PayoutTxPublishedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TaskRunner<BuyerAsTakerTrade> taskRunner = new TaskRunner<>(buyerAsTakerTrade,
                () -> {
                    log.debug("taskRunner at handlePayoutTxPublishedMessage completed");
                    // we are done!
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                BuyerProcessPayoutTxPublishedMessage.class,
                BuyerCommitsPayoutTx.class);
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

            if (tradeMessage.tradeId.equals(processModel.getId())) {
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

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

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.SellerAsOffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.availability.messages.ReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.messages.RequestIsOfferAvailableMessage;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.tasks.seller.CommitDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.tasks.seller.CreateAndSignDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessPayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessRequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendRequestFinalizePayoutTxMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendRequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SignPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import io.bitsquare.trade.states.OffererTradeState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.*;

public class SellerAsOffererProtocol extends TradeProtocol implements SellerProtocol, OffererProtocol {
    private static final Logger log = LoggerFactory.getLogger(SellerAsOffererProtocol.class);

    private final SellerAsOffererTrade sellerAsOffererTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsOffererProtocol(SellerAsOffererTrade trade) {
        super(trade.getProcessModel());
        log.debug("New OffererProtocol " + this);
        this.sellerAsOffererTrade = trade;
        messageHandler = this::handleMessage;

        processModel.getMessageService().addMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void applyMailboxMessage(MailboxMessage mailboxMessage, Trade trade) {
        this.trade = trade;

        log.debug("setMailboxMessage " + mailboxMessage);
        // Find first the actual peer address, as it might have changed in the meantime
        if (mailboxMessage instanceof PayoutTxFinalizedMessage) {
            handle((PayoutTxFinalizedMessage) mailboxMessage);
        }
        else {
            findPeerAddress(processModel.tradingPeer.getP2pSigPubKey(),
                    () -> {
                        if (mailboxMessage instanceof FiatTransferStartedMessage) {
                            handle((FiatTransferStartedMessage) mailboxMessage);
                        }
                        else if (mailboxMessage instanceof DepositTxPublishedMessage) {
                            handle((DepositTxPublishedMessage) mailboxMessage);
                        }
                    },
                    (errorMessage -> {
                        log.error(errorMessage);
                    }));
        }
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
            boolean isOfferOpen = sellerAsOffererTrade.lifeCycleStateProperty().get() == OffererTradeState.LifeCycleState.OFFER_OPEN;

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

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> log.debug("taskRunner at handleTakerDepositPaymentRequestMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessRequestPayDepositMessage.class,
                VerifyTakerAccount.class,
                CreateAndSignContract.class,
                CreateAndSignDepositTx.class,
                SendRequestPublishDepositTxMessage.class
        );
        taskRunner.run();
        startTimeout();
    }

    private void handle(DepositTxPublishedMessage tradeMessage) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> log.debug("taskRunner at handleDepositTxPublishedMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessDepositTxPublishedMessage.class,
                CommitDepositTx.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // After peer has started Fiat tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FiatTransferStartedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> log.debug("taskRunner at handleFiatTransferStartedMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(ProcessFiatTransferStartedMessage.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    @Override
    public void onFiatPaymentReceived() {
        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> {
                    log.debug("taskRunner at handleFiatReceivedUIEvent completed");

                    // we are done!
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                VerifyTakeOfferFeePayment.class,
                SignPayoutTx.class,
                SendRequestFinalizePayoutTxMessage.class
        );
        taskRunner.run();
    }

    private void handle(PayoutTxFinalizedMessage tradeMessage) {
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsOffererTrade,
                () -> log.debug("taskRunner at handleFiatTransferStartedMessage completed"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessPayoutTxFinalizedMessage.class,
                CommitPayoutTx.class,
                SetupPayoutTxLockTimeReachedListener.class
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
                else if (tradeMessage instanceof PayoutTxFinalizedMessage) {
                    handle((PayoutTxFinalizedMessage) tradeMessage);
                }
                else {
                    log.error("Incoming tradeMessage not supported. " + tradeMessage);
                }
            }
        }
    }
}

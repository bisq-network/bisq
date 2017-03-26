/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol;


import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.core.trade.BuyerAsTakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerSendFiatTransferStartedMessage;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerSetupPayoutTxListener;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSignPayoutTx;
import io.bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerCreatesDepositTxInputs;
import io.bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerSignAndPublishDepositTx;
import io.bisq.core.trade.protocol.tasks.taker.*;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.message.trade.PayoutTxPublishedMessage;
import io.bisq.protobuffer.message.trade.PublishDepositTxRequest;
import io.bisq.protobuffer.message.trade.TradeMessage;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerAsTakerProtocol extends TradeProtocol implements BuyerProtocol, TakerProtocol {
    private final BuyerAsTakerTrade buyerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsTakerProtocol(BuyerAsTakerTrade trade) {
        super(trade);

        this.buyerAsTakerTrade = trade;

        processModel.tradingPeer.setPubKeyRing(trade.getOffer().getPubKeyRing());

        Trade.Phase phase = trade.getState().getPhase();
        if (trade.isFiatSent() && !trade.isPayoutPublished()) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        handleTaskRunnerSuccess("BuyerSetupPayoutTxListener");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(BuyerSetupPayoutTxListener.class);
            taskRunner.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(Message message, Trade trade) {
        this.trade = trade;
        final NodeAddress senderNodeAddress = ((MailboxMessage) message).getSenderNodeAddress();
        if (message instanceof PublishDepositTxRequest)
            handle((PublishDepositTxRequest) message, senderNodeAddress);
        else if (message instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) message, senderNodeAddress);
        } else
            log.error("We received an unhandled MailboxMessage" + message.toString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> handleTaskRunnerSuccess("takeAvailableOffer"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                TakerSelectArbitrator.class,
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class,
                TakerCreateTakerFeeTx.class,
                TakerPublishTakerFeeTx.class,
                BuyerAsTakerCreatesDepositTxInputs.class,
                TakerSendPayDepositRequest.class
        );
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PublishDepositTxRequest tradeMessage, NodeAddress sender) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> handleTaskRunnerSuccess("PublishDepositTxRequest"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                TakerProcessPublishDepositTxRequest.class,
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class,
                TakerVerifyAndSignContract.class,
                BuyerAsTakerSignAndPublishDepositTx.class,
                TakerSendDepositTxPublishedMessage.class,
                TakerPublishTradeStatistics.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    @Override
    public void onFiatPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (!trade.isFiatSent()) {
            buyerAsTakerTrade.setState(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED);

            TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentStarted");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });
            taskRunner.addTasks(
                    TakerVerifyMakerAccount.class,
                    TakerVerifyMakerFeePayment.class,
                    BuyerAsMakerSignPayoutTx.class,
                    BuyerSendFiatTransferStartedMessage.class,
                    BuyerSetupPayoutTxListener.class
            );
            taskRunner.run();
        } else {
            log.warn("onFiatPaymentStarted called twice. tradeState=" + trade.getState());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PayoutTxPublishedMessage tradeMessage, NodeAddress peerNodeAddress) {
        log.debug("handle PayoutTxPublishedMessage called");
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> {
                    handleTaskRunnerSuccess("handle PayoutTxPublishedMessage");
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                BuyerProcessPayoutTxPublishedMessage.class
        );
        taskRunner.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof PublishDepositTxRequest) {
            handle((PublishDepositTxRequest) tradeMessage, sender);
        } else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) tradeMessage, sender);
        } else {
            log.error("Incoming message not supported. " + tradeMessage);
        }
    }
}

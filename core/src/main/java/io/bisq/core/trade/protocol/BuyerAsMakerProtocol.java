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
import io.bisq.core.trade.BuyerAsMakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.DepositTxPublishedMessage;
import io.bisq.core.trade.messages.PayDepositRequest;
import io.bisq.core.trade.messages.PayoutTxPublishedMessage;
import io.bisq.core.trade.messages.TradeMessage;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerSendFiatTransferStartedMessage;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerSetupPayoutTxListener;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSignPayoutTx;
import io.bisq.core.trade.protocol.tasks.maker.*;
import io.bisq.core.util.Validator;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.Message;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BuyerAsMakerProtocol extends TradeProtocol implements BuyerProtocol, MakerProtocol {
    private final BuyerAsMakerTrade buyerAsMakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsMakerProtocol(BuyerAsMakerTrade trade) {
        super(trade);

        this.buyerAsMakerTrade = trade;

        Trade.Phase phase = trade.getState().getPhase();
        if (phase == Trade.Phase.TAKER_FEE_PUBLISHED) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        handleTaskRunnerSuccess("MakerSetupDepositTxListener");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(MakerSetupDepositTxListener.class);
            taskRunner.run();
        } else if (trade.isFiatSent() && !trade.isPayoutPublished()) {
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

        if (message instanceof MailboxMessage) {
            MailboxMessage mailboxMessage = (MailboxMessage) message;
            NodeAddress peerNodeAddress = mailboxMessage.getSenderNodeAddress();
            if (message instanceof DepositTxPublishedMessage)
                handle((DepositTxPublishedMessage) message, peerNodeAddress);
            else if (message instanceof PayoutTxPublishedMessage)
                handle((PayoutTxPublishedMessage) message, peerNodeAddress);
            else
                log.error("We received an unhandled MailboxMessage" + message.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, NodeAddress peerNodeAddress) {
        Validator.checkTradeId(processModel.getId(), message);
        checkArgument(message instanceof PayDepositRequest);
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                () -> handleTaskRunnerSuccess("handleTakeOfferRequest"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                MakerProcessPayDepositRequest.class,
                MakerVerifyArbitratorSelection.class,
                MakerVerifyMediatorSelection.class,
                MakerVerifyTakerAccount.class,
                MakerVerifyTakerFeePayment.class,
                MakerCreateAndSignContract.class,
                BuyerAsMakerCreatesAndSignsDepositTx.class,
                MakerSetupDepositTxListener.class,
                MakerSendPublishDepositTxRequest.class
        );

        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(DepositTxPublishedMessage tradeMessage, NodeAddress peerNodeAddress) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                () -> handleTaskRunnerSuccess("handle DepositTxPublishedMessage"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(MakerProcessDepositTxPublishedMessage.class,
                MakerVerifyTakerAccount.class,
                MakerVerifyTakerFeePayment.class,
                MakerPublishTradeStatistics.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    @Override
    public void onFiatPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.isDepositConfirmed() && !trade.isFiatSent()) {
            buyerAsMakerTrade.setState(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED);
            TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentStarted");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });
            taskRunner.addTasks(
                    MakerVerifyTakerAccount.class,
                    MakerVerifyTakerFeePayment.class,
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

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
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
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress) {
        if (tradeMessage instanceof DepositTxPublishedMessage) {
            handle((DepositTxPublishedMessage) tradeMessage, peerNodeAddress);
        } else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) tradeMessage, peerNodeAddress);
        } else //noinspection StatementWithEmptyBody
            if (tradeMessage instanceof PayDepositRequest) {
                // do nothing as we get called the handleTakeOfferRequest method from outside
            } else {
                log.error("Incoming decrypted tradeMessage not supported. " + tradeMessage);
            }
    }
}

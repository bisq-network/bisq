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
import io.bisq.core.trade.protocol.tasks.buyer.BuyerSendFiatTransferStartedMessage;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerSetupListenerForPayoutTx;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerMightBroadcastPayoutTx;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerProcessPayoutTxPublishedMessage;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSignPayoutTx;
import io.bisq.core.trade.protocol.tasks.maker.*;
import io.bisq.core.util.Validator;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.message.trade.DepositTxPublishedMessage;
import io.bisq.protobuffer.message.trade.PayDepositRequest;
import io.bisq.protobuffer.message.trade.PayoutTxPublishedMessage;
import io.bisq.protobuffer.message.trade.TradeMessage;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
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

        Trade.State tradeState = trade.getState();
        Trade.Phase phase = tradeState.getPhase();
        //TODO test case
        if (trade.getPayoutTx() != null &&
                (phase == Trade.Phase.FIAT_RECEIVED || phase == Trade.Phase.PAYOUT_PAID) &&
                tradeState != Trade.State.PAYOUT_BROAD_CASTED) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        handleTaskRunnerSuccess("BuyerAsMakerMightBroadcastPayoutTx");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(BuyerAsMakerMightBroadcastPayoutTx.class);
            taskRunner.run();
        } else if (trade.getPayoutTx() == null && tradeState == Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG
                && phase != Trade.Phase.PAYOUT_PAID) {
            //TODO test case
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        handleTaskRunnerSuccess("SetupListenerForPayoutTx");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(BuyerSetupListenerForPayoutTx.class);
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
                MakerVerifyArbitrationSelection.class,
                MakerVerifyTakerFeePayment.class,
                MakerVerifyTakerAccount.class,
                MakerLoadTakeOfferFeeTx.class,
                MakerCreateAndSignContract.class,
                BuyerAsMakerCreatesAndSignsDepositTx.class,
                MakerSetupDepositBalanceListener.class,
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
                MakerVerifyTakerFeePayment.class,
                MakerVerifyTakerAccount.class,
                MakerPublishTradeStatistics.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    @Override
    public void onFiatPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (buyerAsMakerTrade.getState().ordinal() <= Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG.ordinal()) {
            if (buyerAsMakerTrade.getState() == Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG)
                log.warn("onFiatPaymentStarted called twice. " +
                        "That is expected if the app starts up and the other peer has still not continued.");

            buyerAsMakerTrade.setState(Trade.State.BUYER_CONFIRMED_FIAT_PAYMENT_INITIATED);

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
                    MakerVerifyTakerFeePayment.class,
                    MakerVerifyTakerAccount.class,
                    BuyerAsMakerSignPayoutTx.class,
                    BuyerSendFiatTransferStartedMessage.class,
                    BuyerSetupListenerForPayoutTx.class
            );
            taskRunner.run();
        } else {
            log.warn("onFiatPaymentStarted called twice. " +
                    "That should not happen.\n" +
                    "state=" + buyerAsMakerTrade.getState());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PayoutTxPublishedMessage tradeMessage, NodeAddress peerNodeAddress) {
        log.debug("handle RequestFinalizePayoutTxMessage called");
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                () -> {
                    handleTaskRunnerSuccess("handle FinalizePayoutTxRequest");
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                BuyerAsMakerProcessPayoutTxPublishedMessage.class,
                BuyerAsMakerMightBroadcastPayoutTx.class
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

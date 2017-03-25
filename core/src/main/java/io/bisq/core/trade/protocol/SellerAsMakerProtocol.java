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
import io.bisq.core.trade.SellerAsMakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.maker.*;
import io.bisq.core.trade.protocol.tasks.seller.SellerProcessFiatTransferStartedMessage;
import io.bisq.core.trade.protocol.tasks.seller_as_maker.*;
import io.bisq.core.util.Validator;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.message.trade.*;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class SellerAsMakerProtocol extends TradeProtocol implements SellerProtocol, MakerProtocol {
    private final SellerAsMakerTrade sellerAsMakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsMakerProtocol(SellerAsMakerTrade trade) {
        super(trade);

        this.sellerAsMakerTrade = trade;

        // If we are after the time lock state we need to setup the listener again
        //TODO not sure if that is not called already from the checkPayoutTxTimeLock at tradeProtocol
        Trade.State tradeState = trade.getState();
        Trade.Phase phase = tradeState.getPhase();
        if (trade.getPayoutTx() != null && (phase == Trade.Phase.FIAT_RECEIVED || phase == Trade.Phase.PAYOUT_PAID) &&
                tradeState != Trade.State.PAYOUT_BROAD_CASTED) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        handleTaskRunnerSuccess("SetupPayoutTxLockTimeReachedListener");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(SellerAsMakerBroadcastPayoutTx.class);  //TODO: locktime
            taskRunner.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(Message message, Trade trade) {
        this.trade = trade;

        NodeAddress peerNodeAddress = ((MailboxMessage) message).getSenderNodeAddress();
        if (message instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) message, peerNodeAddress);
        } else {
            if (message instanceof DepositTxPublishedMessage)
                handle((DepositTxPublishedMessage) message, peerNodeAddress);
            else if (message instanceof FiatTransferStartedMessage)
                handle((FiatTransferStartedMessage) message, peerNodeAddress);
            else
                log.error("We received an unhandled MailboxMessage" + message.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, NodeAddress sender) {
        Validator.checkTradeId(processModel.getId(), message);
        checkArgument(message instanceof PayDepositRequest);
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                () -> handleTaskRunnerSuccess("handleTakeOfferRequest"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                MakerProcessPayDepositRequest.class,
                MakerVerifyArbitrationSelection.class,
                MakerVerifyTakerAccount.class,
                MakerVerifyTakerFeePayment.class,
                MakerLoadTakeOfferFeeTx.class,
                MakerCreateAndSignContract.class,
                SellerAsMakerCreatesAndSignsDepositTx.class,
                MakerSetupDepositBalanceListener.class,
                MakerSendPublishDepositTxRequest.class
        );
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling 
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(DepositTxPublishedMessage tradeMessage, NodeAddress sender) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                () -> handleTaskRunnerSuccess("DepositTxPublishedMessage"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(MakerProcessDepositTxPublishedMessage.class,
                MakerPublishTradeStatistics.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // After peer has started Fiat tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(FiatTransferStartedMessage tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                () -> handleTaskRunnerSuccess("FiatTransferStartedMessage"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(SellerProcessFiatTransferStartedMessage.class);
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    @Override
    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (sellerAsMakerTrade.getState().ordinal() <= Trade.State.SELLER_AS_MAKER_SENT_FIAT_PAYMENT_RECEIPT_MSG.ordinal()) {
            if (sellerAsMakerTrade.getState() == Trade.State.SELLER_AS_MAKER_SENT_FIAT_PAYMENT_RECEIPT_MSG)
                log.warn("onFiatPaymentReceived called twice. " +
                        "That is expected if the app starts up and the other peer has still not continued.");

            sellerAsMakerTrade.setState(Trade.State.SELLER_CONFIRMED_FIAT_PAYMENT_RECEIPT);

            TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentReceived");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });

            taskRunner.addTasks(
                    MakerVerifyTakerAccount.class,
                    MakerVerifyTakerFeePayment.class,
                    SellerAsMakerSignPayoutTx.class,  //TODO: locktime
                    SellerAsMaker___SendFinalizePayoutTxRequest.class  //TODO: locktime
            );
            taskRunner.run();
        } else {
            log.warn("onFiatPaymentReceived called twice. " +
                    "That should not happen.\n" +
                    "state=" + sellerAsMakerTrade.getState());
        }
    }

    private void handle(PayoutTxPublishedMessage tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                () -> {
                    handleTaskRunnerSuccess("PayoutTxFinalizedMessage");
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                SellerAsMaker___ProcessPayoutTxFinalizedMessage.class,
                SellerAsMakerBroadcastPayoutTx.class  //TODO: locktime
        );
        taskRunner.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof DepositTxPublishedMessage) {
            handle((DepositTxPublishedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof FiatTransferStartedMessage) {
            handle((FiatTransferStartedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) tradeMessage, sender);
        } else {
            log.error("Incoming tradeMessage not supported. " + tradeMessage);
        }
    }
}

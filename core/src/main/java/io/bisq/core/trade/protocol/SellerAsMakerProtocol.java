/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol;


import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.core.trade.SellerAsMakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import io.bisq.core.trade.messages.DepositTxPublishedMessage;
import io.bisq.core.trade.messages.PayDepositRequest;
import io.bisq.core.trade.messages.TradeMessage;
import io.bisq.core.trade.protocol.tasks.CheckIfPeerIsBanned;
import io.bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import io.bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import io.bisq.core.trade.protocol.tasks.maker.*;
import io.bisq.core.trade.protocol.tasks.seller.*;
import io.bisq.core.trade.protocol.tasks.seller_as_maker.SellerAsMakerCreatesAndSignsDepositTx;
import io.bisq.core.util.Validator;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
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

        Trade.Phase phase = trade.getState().getPhase();
        if (phase == Trade.Phase.TAKER_FEE_PUBLISHED) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> handleTaskRunnerSuccess("MakerSetupDepositTxListener"),
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(MakerSetupDepositTxListener.class);
            taskRunner.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(NetworkEnvelope networkEnvelop, Trade trade) {
        this.trade = trade;

        NodeAddress peerNodeAddress = ((MailboxMessage) networkEnvelop).getSenderNodeAddress();
        if (networkEnvelop instanceof DepositTxPublishedMessage)
            handle((DepositTxPublishedMessage) networkEnvelop, peerNodeAddress);
        else if (networkEnvelop instanceof CounterCurrencyTransferStartedMessage)
            handle((CounterCurrencyTransferStartedMessage) networkEnvelop, peerNodeAddress);
        else
            log.error("We received an unhandled MailboxMessage" + networkEnvelop.toString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, NodeAddress sender, ErrorMessageHandler errorMessageHandler) {
        Validator.checkTradeId(processModel.getOfferId(), message);
        checkArgument(message instanceof PayDepositRequest);
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                () -> handleTaskRunnerSuccess("handleTakeOfferRequest"),
                errorMessage -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(errorMessage);
                });

        taskRunner.addTasks(
                MakerProcessPayDepositRequest.class,
                CheckIfPeerIsBanned.class,
                MakerVerifyArbitratorSelection.class,
                MakerVerifyMediatorSelection.class,
                MakerVerifyTakerAccount.class,
                VerifyPeersAccountAgeWitness.class,
                MakerVerifyTakerFeePayment.class,
                MakerCreateAndSignContract.class,
                SellerAsMakerCreatesAndSignsDepositTx.class,
                MakerSetupDepositTxListener.class,
                MakerSendPublishDepositTxRequest.class
        );

        // We don't start a timeout because if we don't receive the peers DepositTxPublishedMessage we still
        // will get set the deposit tx in MakerSetupDepositTxListener once seen in the network
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(DepositTxPublishedMessage tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                () -> {
                    handleTaskRunnerSuccess("DepositTxPublishedMessage");
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                MakerProcessDepositTxPublishedMessage.class,
                PublishTradeStatistics.class,
                MakerVerifyTakerAccount.class,
                MakerVerifyTakerFeePayment.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // After peer has started Fiat tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(CounterCurrencyTransferStartedMessage tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                () -> handleTaskRunnerSuccess("CounterCurrencyTransferStartedMessage"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                SellerProcessCounterCurrencyTransferStartedMessage.class,
                MakerVerifyTakerAccount.class,
                MakerVerifyTakerFeePayment.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    @Override
    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.getPayoutTx() == null) {
            sellerAsMakerTrade.setState(Trade.State.SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT);
            TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentReceived 1");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });

            taskRunner.addTasks(
                    CheckIfPeerIsBanned.class,
                    MakerVerifyTakerAccount.class,
                    MakerVerifyTakerFeePayment.class,
                    SellerSignAndFinalizePayoutTx.class,
                    SellerBroadcastPayoutTx.class,
                    SellerSendPayoutTxPublishedMessage.class
            );
            taskRunner.run();
        } else {
            // we don't set the state as we have already a later phase reached
            log.info("onFiatPaymentReceived called twice. " +
                    "That can happen if message did not arrive first time and we send msg again.\n" +
                    "state=" + sellerAsMakerTrade.getState());

            TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentReceived 2");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });

            taskRunner.addTasks(
                    CheckIfPeerIsBanned.class,
                    MakerVerifyTakerAccount.class,
                    MakerVerifyTakerFeePayment.class,
                    SellerSendPayoutTxPublishedMessage.class
            );
            taskRunner.run();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof DepositTxPublishedMessage) {
            handle((DepositTxPublishedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) tradeMessage, sender);
        } else {
            log.error("Incoming tradeMessage not supported. " + tradeMessage);
        }
    }
}

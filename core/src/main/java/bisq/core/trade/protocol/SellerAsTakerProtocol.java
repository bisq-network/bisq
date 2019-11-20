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

package bisq.core.trade.protocol;


import bisq.core.offer.Offer;
import bisq.core.trade.SellerAsTakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.messages.DelayedPayoutTxSignatureResponse;
import bisq.core.trade.messages.InputsForDepositTxResponse;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.tasks.seller.SellerBroadcastPayoutTx;
import bisq.core.trade.protocol.tasks.seller.SellerCreatesDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.seller.SellerFinalizesDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.seller.SellerProcessCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.seller.SellerProcessDelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.tasks.seller.SellerPublishesDepositTx;
import bisq.core.trade.protocol.tasks.seller.SellerSendDelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.tasks.seller.SellerSendPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.seller.SellerSendsDepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.tasks.seller.SellerSignAndFinalizePayoutTx;
import bisq.core.trade.protocol.tasks.seller.SellerSignsDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.seller_as_taker.SellerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.tasks.seller_as_taker.SellerAsTakerSignsDepositTx;
import bisq.core.trade.protocol.tasks.taker.CreateTakerFeeTx;
import bisq.core.trade.protocol.tasks.taker.TakerProcessesInputsForDepositTxResponse;
import bisq.core.trade.protocol.tasks.taker.TakerPublishFeeTx;
import bisq.core.trade.protocol.tasks.taker.TakerSendInputsForDepositTxRequest;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyAndSignContract;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyMakerAccount;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyMakerFeePayment;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsTakerProtocol extends TradeProtocol implements SellerProtocol, TakerProtocol {
    private final SellerAsTakerTrade sellerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol(SellerAsTakerTrade trade) {
        super(trade);

        this.sellerAsTakerTrade = trade;

        Offer offer = checkNotNull(trade.getOffer());
        processModel.getTradingPeer().setPubKeyRing(offer.getPubKeyRing());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxTradeMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress) {
        super.doApplyMailboxTradeMessage(tradeMessage, peerNodeAddress);

        if (tradeMessage instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) tradeMessage, peerNodeAddress);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> handleTaskRunnerSuccess("takeAvailableOffer"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class,
                CreateTakerFeeTx.class,
                SellerAsTakerCreatesDepositTxInputs.class,
                TakerSendInputsForDepositTxRequest.class
        );

        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(InputsForDepositTxResponse tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> {
                    stopTimeout();
                    handleTaskRunnerSuccess(tradeMessage, "PublishDepositTxRequest");
                },
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                TakerProcessesInputsForDepositTxResponse.class,
                ApplyFilter.class,
                TakerVerifyMakerAccount.class,
                VerifyPeersAccountAgeWitness.class,
                TakerVerifyMakerFeePayment.class,
                TakerVerifyAndSignContract.class,
                TakerPublishFeeTx.class,
                SellerAsTakerSignsDepositTx.class,
                SellerCreatesDelayedPayoutTx.class,
                SellerSendDelayedPayoutTxSignatureRequest.class
        );
        taskRunner.run();
    }

    private void handle(DelayedPayoutTxSignatureResponse tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> {
                    stopTimeout();
                    handleTaskRunnerSuccess(tradeMessage, "PublishDepositTxRequest");
                },
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                SellerProcessDelayedPayoutTxSignatureResponse.class,
                SellerSignsDelayedPayoutTx.class,
                SellerFinalizesDelayedPayoutTx.class,
                SellerPublishesDepositTx.class,
                SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                PublishTradeStatistics.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // After peer has started Fiat tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(CounterCurrencyTransferStartedMessage tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> handleTaskRunnerSuccess(tradeMessage, "CounterCurrencyTransferStartedMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                SellerProcessCounterCurrencyTransferStartedMessage.class,
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer received" button, so we release the funds for payout
    @Override
    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.getPayoutTx() == null) {
            sellerAsTakerTrade.setState(Trade.State.SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT);
            TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentReceived 1");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });

            taskRunner.addTasks(
                    ApplyFilter.class,
                    TakerVerifyMakerAccount.class,
                    TakerVerifyMakerFeePayment.class,
                    SellerSignAndFinalizePayoutTx.class,
                    SellerBroadcastPayoutTx.class,
                    SellerSendPayoutTxPublishedMessage.class
            );
            taskRunner.run();
        } else {
            // we don't set the state as we have already a higher phase reached
            log.info("onFiatPaymentReceived called twice. " +
                    "That can happen if message did not arrive the first time and we send msg again.\n" +
                    "state=" + sellerAsTakerTrade.getState());

            TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentReceived 2");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });

            taskRunner.addTasks(
                    ApplyFilter.class,
                    TakerVerifyMakerAccount.class,
                    TakerVerifyMakerFeePayment.class,
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
        super.doHandleDecryptedMessage(tradeMessage, sender);

        log.info("Received {} from {} with tradeId {} and uid {}",
                tradeMessage.getClass().getSimpleName(), sender, tradeMessage.getTradeId(), tradeMessage.getUid());

        if (tradeMessage instanceof InputsForDepositTxResponse) {
            handle((InputsForDepositTxResponse) tradeMessage, sender);
        } else if (tradeMessage instanceof DelayedPayoutTxSignatureResponse) {
            handle((DelayedPayoutTxSignatureResponse) tradeMessage, sender);
        } else if (tradeMessage instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) tradeMessage, sender);
        }
    }
}

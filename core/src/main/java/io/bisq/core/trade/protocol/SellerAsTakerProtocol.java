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
import io.bisq.core.trade.SellerAsTakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import io.bisq.core.trade.messages.PublishDepositTxRequest;
import io.bisq.core.trade.messages.TradeMessage;
import io.bisq.core.trade.protocol.tasks.CheckIfPeerIsBanned;
import io.bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import io.bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import io.bisq.core.trade.protocol.tasks.seller.*;
import io.bisq.core.trade.protocol.tasks.seller_as_taker.SellerAsTakerCreatesDepositTxInputs;
import io.bisq.core.trade.protocol.tasks.seller_as_taker.SellerAsTakerSignAndPublishDepositTx;
import io.bisq.core.trade.protocol.tasks.taker.*;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerAsTakerProtocol extends TradeProtocol implements SellerProtocol, TakerProtocol {
    private final SellerAsTakerTrade sellerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerProtocol(SellerAsTakerTrade trade) {
        super(trade);

        this.sellerAsTakerTrade = trade;

        processModel.getTradingPeer().setPubKeyRing(trade.getOffer().getPubKeyRing());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(NetworkEnvelope networkEnvelop, Trade trade) {
        this.trade = trade;

        if (networkEnvelop instanceof MailboxMessage) {
            NodeAddress peerNodeAddress = ((MailboxMessage) networkEnvelop).getSenderNodeAddress();
            if (networkEnvelop instanceof PublishDepositTxRequest)
                handle((PublishDepositTxRequest) networkEnvelop, peerNodeAddress);
            else if (networkEnvelop instanceof CounterCurrencyTransferStartedMessage)
                handle((CounterCurrencyTransferStartedMessage) networkEnvelop, peerNodeAddress);
            else
                log.error("We received an unhandled MailboxMessage" + networkEnvelop.toString());
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
                TakerSelectArbitrator.class,
                TakerSelectMediator.class,
                CreateTakerFeeTx.class,
                SellerAsTakerCreatesDepositTxInputs.class,
                TakerSendPayDepositRequest.class
        );

        //TODO if peer does get an error he does not respond and all we get is the timeout now knowing why it failed.
        // We should add an error message the peer sends us in such cases.
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PublishDepositTxRequest tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                () -> {
                    stopTimeout();
                    handleTaskRunnerSuccess("PublishDepositTxRequest");
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                TakerProcessPublishDepositTxRequest.class,
                CheckIfPeerIsBanned.class,
                TakerVerifyMakerAccount.class,
                VerifyPeersAccountAgeWitness.class,
                TakerVerifyMakerFeePayment.class,
                TakerVerifyAndSignContract.class,
                SellerAsTakerSignAndPublishDepositTx.class,
                TakerSendDepositTxPublishedMessage.class,
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
                () -> handleTaskRunnerSuccess("CounterCurrencyTransferStartedMessage"),
                this::handleTaskRunnerFault);

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

    // User clicked the "bank transfer received" button, so we release the funds for pay out
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
                    CheckIfPeerIsBanned.class,
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
                    "That can happen if message did not arrive first time and we send msg again.\n" +
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
                    CheckIfPeerIsBanned.class,
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
        if (tradeMessage instanceof PublishDepositTxRequest) {
            handle((PublishDepositTxRequest) tradeMessage, sender);
        } else if (tradeMessage instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) tradeMessage, sender);
        } else {
            log.error("Incoming message not supported. " + tradeMessage);
        }
    }
}

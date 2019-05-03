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


import bisq.core.trade.SellerAsMakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.messages.DepositTxPublishedMessage;
import bisq.core.trade.messages.PayDepositRequest;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.tasks.maker.MakerProcessDepositTxPublishedMessage;
import bisq.core.trade.protocol.tasks.maker.MakerProcessPayDepositRequest;
import bisq.core.trade.protocol.tasks.maker.MakerSendPublishDepositTxRequest;
import bisq.core.trade.protocol.tasks.maker.MakerSetupDepositTxListener;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerAccount;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerFeePayment;
import bisq.core.trade.protocol.tasks.seller.SellerBroadcastPayoutTx;
import bisq.core.trade.protocol.tasks.seller.SellerProcessCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.seller.SellerSendPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.seller.SellerSignAndFinalizePayoutTx;
import bisq.core.trade.protocol.tasks.seller.SellerVerifiesPeersAccountAge;
import bisq.core.trade.protocol.tasks.seller_as_maker.SellerAsMakerCreatesAndSignsDepositTx;
import bisq.core.util.Validator;

import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkEnvelope;

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
    public void doApplyMailboxMessage(NetworkEnvelope networkEnvelope, Trade trade) {
        this.trade = trade;

        if (networkEnvelope instanceof MailboxMessage) {
            NodeAddress peerNodeAddress = ((MailboxMessage) networkEnvelope).getSenderNodeAddress();
            if (networkEnvelope instanceof TradeMessage) {
                TradeMessage tradeMessage = (TradeMessage) networkEnvelope;
                log.info("Received {} as MailboxMessage from {} with tradeId {} and uid {}",
                        tradeMessage.getClass().getSimpleName(), peerNodeAddress, tradeMessage.getTradeId(), tradeMessage.getUid());

                if (tradeMessage instanceof DepositTxPublishedMessage)
                    handle((DepositTxPublishedMessage) tradeMessage, peerNodeAddress);
                else if (tradeMessage instanceof CounterCurrencyTransferStartedMessage)
                    handle((CounterCurrencyTransferStartedMessage) tradeMessage, peerNodeAddress);
                else
                    log.error("We received an unhandled tradeMessage" + tradeMessage.toString());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage tradeMessage, NodeAddress sender, ErrorMessageHandler errorMessageHandler) {
        Validator.checkTradeId(processModel.getOfferId(), tradeMessage);
        checkArgument(tradeMessage instanceof PayDepositRequest);
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsMakerTrade,
                () -> handleTaskRunnerSuccess(tradeMessage, "handleTakeOfferRequest"),
                errorMessage -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(tradeMessage, errorMessage);
                });

        taskRunner.addTasks(
                MakerProcessPayDepositRequest.class,
                ApplyFilter.class,
                MakerVerifyTakerAccount.class,
                VerifyPeersAccountAgeWitness.class,
                SellerVerifiesPeersAccountAge.class,
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
                    handleTaskRunnerSuccess(tradeMessage, "DepositTxPublishedMessage");
                },
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

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
                () -> handleTaskRunnerSuccess(tradeMessage, "CounterCurrencyTransferStartedMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

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
                    ApplyFilter.class,
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
                    ApplyFilter.class,
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
        log.info("Received {} from {} with tradeId {} and uid {}",
                tradeMessage.getClass().getSimpleName(), sender, tradeMessage.getTradeId(), tradeMessage.getUid());

        if (tradeMessage instanceof DepositTxPublishedMessage) {
            handle((DepositTxPublishedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) tradeMessage, sender);
        } else {
            log.error("Incoming tradeMessage not supported. " + tradeMessage);
        }
    }
}

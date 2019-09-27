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
import bisq.core.trade.BuyerAsTakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.DelayedPayoutTxSignatureRequest;
import bisq.core.trade.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.messages.InputsForDepositTxResponse;
import bisq.core.trade.messages.PayoutTxPublishedMessage;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.tasks.buyer.BuyerProcessDelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.tasks.buyer.BuyerProcessDepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerSendsDelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.tasks.buyer.BuyerSetupDepositTxListener;
import bisq.core.trade.protocol.tasks.buyer.BuyerSetupPayoutTxListener;
import bisq.core.trade.protocol.tasks.buyer.BuyerSignPayoutTx;
import bisq.core.trade.protocol.tasks.buyer.BuyerSignsDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.buyer.BuyerVerifiesDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerSendsDepositTxMessage;
import bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerSignsDepositTx;
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
public class BuyerAsTakerProtocol extends TradeProtocol implements BuyerProtocol, TakerProtocol {
    private final BuyerAsTakerTrade buyerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsTakerProtocol(BuyerAsTakerTrade trade) {
        super(trade);

        this.buyerAsTakerTrade = trade;

        Offer offer = checkNotNull(trade.getOffer());
        processModel.getTradingPeer().setPubKeyRing(offer.getPubKeyRing());

        Trade.Phase phase = trade.getState().getPhase();
        if (phase == Trade.Phase.TAKER_FEE_PUBLISHED) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> handleTaskRunnerSuccess("BuyerSetupDepositTxListener"),
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(BuyerSetupDepositTxListener.class);
            taskRunner.run();
        } else if (trade.isFiatSent() && !trade.isPayoutPublished()) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> handleTaskRunnerSuccess("BuyerSetupPayoutTxListener"),
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(BuyerSetupPayoutTxListener.class);
            taskRunner.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxTradeMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress) {
        super.doApplyMailboxTradeMessage(tradeMessage, peerNodeAddress);

        if (tradeMessage instanceof DepositTxAndDelayedPayoutTxMessage) {
            handle((DepositTxAndDelayedPayoutTxMessage) tradeMessage, peerNodeAddress);
        } else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) tradeMessage, peerNodeAddress);
        }
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
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class,
                CreateTakerFeeTx.class,
                BuyerAsTakerCreatesDepositTxInputs.class,
                TakerSendInputsForDepositTxRequest.class
        );

        //TODO if peer does get an error he does not respond and all we get is the timeout now knowing why it failed.
        // We should add an error message the peer sends us in such cases.
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(InputsForDepositTxResponse tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
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
                BuyerAsTakerSignsDepositTx.class,
                BuyerSetupDepositTxListener.class,
                BuyerAsTakerSendsDepositTxMessage.class
        );
        taskRunner.run();
    }

    private void handle(DelayedPayoutTxSignatureRequest tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> {
                    handleTaskRunnerSuccess(tradeMessage, "handle DelayedPayoutTxSignatureRequest");
                },
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));
        taskRunner.addTasks(
                BuyerProcessDelayedPayoutTxSignatureRequest.class,
                BuyerSignsDelayedPayoutTx.class,
                BuyerSendsDelayedPayoutTxSignatureResponse.class
        );
        taskRunner.run();
    }


    private void handle(DepositTxAndDelayedPayoutTxMessage tradeMessage, NodeAddress peerNodeAddress) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> {
                    handleTaskRunnerSuccess(tradeMessage, "handle DepositTxAndDelayedPayoutTxMessage");
                },
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));
        taskRunner.addTasks(
                BuyerProcessDepositTxAndDelayedPayoutTxMessage.class,
                BuyerVerifiesDelayedPayoutTx.class,
                PublishTradeStatistics.class
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
                    ApplyFilter.class,
                    TakerVerifyMakerAccount.class,
                    TakerVerifyMakerFeePayment.class,
                    BuyerSignPayoutTx.class,
                    BuyerSendCounterCurrencyTransferStartedMessage.class,
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
                () -> handleTaskRunnerSuccess(tradeMessage, "handle PayoutTxPublishedMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

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
        super.doHandleDecryptedMessage(tradeMessage, sender);

        log.info("Received {} from {} with tradeId {} and uid {}",
                tradeMessage.getClass().getSimpleName(), sender, tradeMessage.getTradeId(), tradeMessage.getUid());

        if (tradeMessage instanceof InputsForDepositTxResponse) {
            handle((InputsForDepositTxResponse) tradeMessage, sender);
        } else if (tradeMessage instanceof DelayedPayoutTxSignatureRequest) {
            handle((DelayedPayoutTxSignatureRequest) tradeMessage, sender);
        } else if (tradeMessage instanceof DepositTxAndDelayedPayoutTxMessage) {
            handle((DepositTxAndDelayedPayoutTxMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) tradeMessage, sender);
        }
    }
}

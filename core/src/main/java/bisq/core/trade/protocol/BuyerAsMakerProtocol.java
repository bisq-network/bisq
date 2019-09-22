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

import bisq.core.trade.BuyerAsMakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.DelayedPayoutTxSignatureRequest;
import bisq.core.trade.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.messages.InputsForDepositTxRequest;
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
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSendsInputsForDepositTxResponse;
import bisq.core.trade.protocol.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.tasks.maker.MakerProcessesInputsForDepositTxRequest;
import bisq.core.trade.protocol.tasks.maker.MakerSetsLockTime;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerAccount;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerFeePayment;
import bisq.core.util.Validator;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

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
    public void handleTakeOfferRequest(InputsForDepositTxRequest tradeMessage,
                                       NodeAddress peerNodeAddress,
                                       ErrorMessageHandler errorMessageHandler) {
        Validator.checkTradeId(processModel.getOfferId(), tradeMessage);
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                () -> handleTaskRunnerSuccess(tradeMessage, "handleTakeOfferRequest"),
                errorMessage -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(errorMessage);
                });
        taskRunner.addTasks(
                MakerProcessesInputsForDepositTxRequest.class,
                ApplyFilter.class,
                MakerVerifyTakerAccount.class,
                VerifyPeersAccountAgeWitness.class,
                MakerVerifyTakerFeePayment.class,
                MakerSetsLockTime.class,
                MakerCreateAndSignContract.class,
                BuyerAsMakerCreatesAndSignsDepositTx.class,
                BuyerSetupDepositTxListener.class,
                BuyerAsMakerSendsInputsForDepositTxResponse.class
        );
        // We don't use a timeout here because if the DepositTxPublishedMessage does not arrive we
        // get the deposit tx set at MakerSetupDepositTxListener once it is seen in the bitcoin network
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(DelayedPayoutTxSignatureRequest tradeMessage, NodeAddress peerNodeAddress) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
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

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
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
                    ApplyFilter.class,
                    MakerVerifyTakerAccount.class,
                    MakerVerifyTakerFeePayment.class,
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
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
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

        if (tradeMessage instanceof DelayedPayoutTxSignatureRequest) {
            handle((DelayedPayoutTxSignatureRequest) tradeMessage, sender);
        } else if (tradeMessage instanceof DepositTxAndDelayedPayoutTxMessage) {
            handle((DepositTxAndDelayedPayoutTxMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) tradeMessage, sender);
        }
    }
}

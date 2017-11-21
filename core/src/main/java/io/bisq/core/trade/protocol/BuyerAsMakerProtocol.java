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
import io.bisq.core.trade.BuyerAsMakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.DepositTxPublishedMessage;
import io.bisq.core.trade.messages.PayDepositRequest;
import io.bisq.core.trade.messages.PayoutTxPublishedMessage;
import io.bisq.core.trade.messages.TradeMessage;
import io.bisq.core.trade.protocol.tasks.CheckIfPeerIsBanned;
import io.bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import io.bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;
import io.bisq.core.trade.protocol.tasks.buyer.BuyerSetupPayoutTxListener;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import io.bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSignPayoutTx;
import io.bisq.core.trade.protocol.tasks.maker.*;
import io.bisq.core.util.Validator;
import io.bisq.network.p2p.MailboxMessage;
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
                    () -> handleTaskRunnerSuccess("MakerSetupDepositTxListener"),
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(MakerSetupDepositTxListener.class);
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
    public void doApplyMailboxMessage(NetworkEnvelope networkEnvelop, Trade trade) {
        this.trade = trade;

        if (networkEnvelop instanceof MailboxMessage) {
            MailboxMessage mailboxMessage = (MailboxMessage) networkEnvelop;
            NodeAddress peerNodeAddress = mailboxMessage.getSenderNodeAddress();
            if (networkEnvelop instanceof DepositTxPublishedMessage)
                handle((DepositTxPublishedMessage) networkEnvelop, peerNodeAddress);
            else if (networkEnvelop instanceof PayoutTxPublishedMessage)
                handle((PayoutTxPublishedMessage) networkEnvelop, peerNodeAddress);
            else
                log.error("We received an unhandled MailboxMessage" + networkEnvelop.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, NodeAddress peerNodeAddress, ErrorMessageHandler errorMessageHandler) {
        Validator.checkTradeId(processModel.getOfferId(), message);
        checkArgument(message instanceof PayDepositRequest);
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
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
                BuyerAsMakerCreatesAndSignsDepositTx.class,
                MakerSetupDepositTxListener.class,
                MakerSendPublishDepositTxRequest.class
        );
        // We don't use a timeout here because if the DepositTxPublishedMessage does not arrive we
        // get the deposit tx set at MakerSetupDepositTxListener once it is seen in the bitcoin network
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(DepositTxPublishedMessage tradeMessage, NodeAddress peerNodeAddress) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                () -> {
                    handleTaskRunnerSuccess("handle DepositTxPublishedMessage");
                },
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                MakerProcessDepositTxPublishedMessage.class,
                MakerVerifyTakerAccount.class,
                MakerVerifyTakerFeePayment.class,
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
                    CheckIfPeerIsBanned.class,
                    MakerVerifyTakerAccount.class,
                    MakerVerifyTakerFeePayment.class,
                    BuyerAsMakerSignPayoutTx.class,
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

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                () -> handleTaskRunnerSuccess("handle PayoutTxPublishedMessage"),
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

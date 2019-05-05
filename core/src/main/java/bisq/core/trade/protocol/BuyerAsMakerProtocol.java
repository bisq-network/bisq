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
import bisq.core.trade.messages.DepositTxPublishedMessage;
import bisq.core.trade.messages.PayDepositRequest;
import bisq.core.trade.messages.PayoutTxPublishedMessage;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerSetupPayoutTxListener;
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSignPayoutTx;
import bisq.core.trade.protocol.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.tasks.maker.MakerProcessDepositTxPublishedMessage;
import bisq.core.trade.protocol.tasks.maker.MakerProcessPayDepositRequest;
import bisq.core.trade.protocol.tasks.maker.MakerSendPublishDepositTxRequest;
import bisq.core.trade.protocol.tasks.maker.MakerSetupDepositTxListener;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerAccount;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerFeePayment;
import bisq.core.util.Validator;

import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkEnvelope;

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
    public void doApplyMailboxMessage(NetworkEnvelope networkEnvelope, Trade trade) {
        this.trade = trade;

        if (networkEnvelope instanceof MailboxMessage) {
            MailboxMessage mailboxMessage = (MailboxMessage) networkEnvelope;
            NodeAddress peerNodeAddress = mailboxMessage.getSenderNodeAddress();
            if (networkEnvelope instanceof TradeMessage) {
                TradeMessage tradeMessage = (TradeMessage) networkEnvelope;
                log.info("Received {} as MailboxMessage from {} with tradeId {} and uid {}",
                        tradeMessage.getClass().getSimpleName(), peerNodeAddress, tradeMessage.getTradeId(), tradeMessage.getUid());
                if (tradeMessage instanceof DepositTxPublishedMessage)
                    handle((DepositTxPublishedMessage) tradeMessage, peerNodeAddress);
                else if (tradeMessage instanceof PayoutTxPublishedMessage)
                    handle((PayoutTxPublishedMessage) tradeMessage, peerNodeAddress);
                else
                    log.error("We received an unhandled tradeMessage" + tradeMessage.toString());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage tradeMessage, NodeAddress peerNodeAddress, ErrorMessageHandler errorMessageHandler) {
        Validator.checkTradeId(processModel.getOfferId(), tradeMessage);
        checkArgument(tradeMessage instanceof PayDepositRequest);
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                () -> handleTaskRunnerSuccess(tradeMessage, "handleTakeOfferRequest"),
                errorMessage -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(errorMessage);
                });
        taskRunner.addTasks(
                MakerProcessPayDepositRequest.class,
                ApplyFilter.class,
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
                    handleTaskRunnerSuccess(tradeMessage, "handle DepositTxPublishedMessage");
                },
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));
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
                    ApplyFilter.class,
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
        log.info("Received {} from {} with tradeId {} and uid {}",
                tradeMessage.getClass().getSimpleName(), sender, tradeMessage.getTradeId(), tradeMessage.getUid());

        if (tradeMessage instanceof DepositTxPublishedMessage) {
            handle((DepositTxPublishedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) tradeMessage, sender);
        } else //noinspection StatementWithEmptyBody
            if (tradeMessage instanceof PayDepositRequest) {
                // do nothing as we get called the handleTakeOfferRequest method from outside
            } else {
                log.error("Incoming decrypted tradeMessage not supported. " + tradeMessage);
            }
    }
}

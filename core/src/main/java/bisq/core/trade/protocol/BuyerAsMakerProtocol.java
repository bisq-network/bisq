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
import bisq.core.trade.protocol.tasks.buyer.BuyerVerifiesFinalDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.buyer.BuyerVerifiesPreparedDelayedPayoutTx;
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSendsInputsForDepositTxResponse;
import bisq.core.trade.protocol.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.tasks.maker.MakerProcessesInputsForDepositTxRequest;
import bisq.core.trade.protocol.tasks.maker.MakerSetsLockTime;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerFeePayment;

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
                    () -> handleTaskRunnerSuccess(BuyerEvent.STARTUP),
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(BuyerSetupDepositTxListener.class);
            taskRunner.run();
        } else if (trade.isFiatSent() && !trade.isPayoutPublished()) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> handleTaskRunnerSuccess(BuyerEvent.STARTUP),
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(BuyerSetupPayoutTxListener.class);
            if (trade.getState() == Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG ||
                    trade.getState() == Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG) {
                // In case we have not received an ACK from the CounterCurrencyTransferStartedMessage we re-send it
                // periodically in BuyerSendCounterCurrencyTransferStartedMessage
                taskRunner.addTasks(BuyerSendCounterCurrencyTransferStartedMessage.class);
            }
            taskRunner.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxTradeMessage(TradeMessage message, NodeAddress peer) {
        super.doApplyMailboxTradeMessage(message, peer);

        if (message instanceof DepositTxAndDelayedPayoutTxMessage) {
            handle((DepositTxAndDelayedPayoutTxMessage) message, peer);
        } else if (message instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) message, peer);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(InputsForDepositTxRequest message,
                                       NodeAddress peer,
                                       ErrorMessageHandler errorMessageHandler) {
        from(Trade.Phase.INIT)
                .on(message)
                .from(peer)
                .withTimeout(30)
                .process(() -> {
                    TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                            () -> handleTaskRunnerSuccess(message),
                            errorMessage -> {
                                errorMessageHandler.handleErrorMessage(errorMessage);
                                handleTaskRunnerFault(errorMessage);
                            });
                    taskRunner.addTasks(
                            MakerProcessesInputsForDepositTxRequest.class,
                            ApplyFilter.class,
                            VerifyPeersAccountAgeWitness.class,
                            MakerVerifyTakerFeePayment.class,
                            MakerSetsLockTime.class,
                            MakerCreateAndSignContract.class,
                            BuyerAsMakerCreatesAndSignsDepositTx.class,
                            BuyerSetupDepositTxListener.class,
                            BuyerAsMakerSendsInputsForDepositTxResponse.class
                    );
                    taskRunner.run();
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming messages Take offer process
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(DelayedPayoutTxSignatureRequest message, NodeAddress peer) {
        from(Trade.Phase.TAKER_FEE_PUBLISHED)
                .on(message)
                .from(peer)
                .withTimeout(30)
                .process(() -> {
                    TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                            () -> handleTaskRunnerSuccess(message),
                            errorMessage -> handleTaskRunnerFault(message, errorMessage));
                    taskRunner.addTasks(
                            BuyerProcessDelayedPayoutTxSignatureRequest.class,
                            BuyerVerifiesPreparedDelayedPayoutTx.class,
                            BuyerSignsDelayedPayoutTx.class,
                            BuyerSendsDelayedPayoutTxSignatureResponse.class
                    );
                    taskRunner.run();
                });
    }

    // The DepositTxAndDelayedPayoutTxMessage is a mailbox message as earlier we use only the deposit tx which can
    // be also received from the network once published.
    // Now we send the delayed payout tx as well and with that this message is mandatory for continuing the protocol.
    // We do not support mailbox message handling during the take offer process as it is expected that both peers
    // are online.
    // For backward compatibility and extra resilience we still keep DepositTxAndDelayedPayoutTxMessage as a
    // mailbox message but the stored in mailbox case is not expected and the seller would try to send the message again
    // in the hope to reach the buyer directly.
    private void handle(DepositTxAndDelayedPayoutTxMessage message, NodeAddress peer) {
        fromAny(Trade.Phase.TAKER_FEE_PUBLISHED, Trade.Phase.DEPOSIT_PUBLISHED)
                .on(message)
                .from(peer)
                .condition(trade.getDepositTx() == null || trade.getDelayedPayoutTx() == null,
                        () -> {
                            log.warn("We received a DepositTxAndDelayedPayoutTxMessage but we have already processed the deposit and " +
                                    "delayed payout tx so we ignore the message. This can happen if the ACK message to the peer did not " +
                                    "arrive and the peer repeats sending us the message. We send another ACK msg.");
                            stopTimeout();
                            sendAckMessage(message, true, null);
                            processModel.removeMailboxMessageAfterProcessing(trade);
                        })
                .process(() -> {
                    TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                            () -> {
                                stopTimeout();
                                handleTaskRunnerSuccess(message);
                            },
                            errorMessage -> handleTaskRunnerFault(message, errorMessage));
                    taskRunner.addTasks(
                            BuyerProcessDepositTxAndDelayedPayoutTxMessage.class,
                            BuyerVerifiesFinalDelayedPayoutTx.class,
                            PublishTradeStatistics.class
                    );
                    taskRunner.run();
                    processModel.witnessDebugLog(buyerAsMakerTrade);
                });


    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onFiatPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        from(Trade.Phase.DEPOSIT_CONFIRMED)
                .on(BuyerEvent.PAYMENT_SENT)
                .condition(!wasDisputed())
                .process(() -> {
                    buyerAsMakerTrade.setState(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED);
                    TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                            () -> {
                                resultHandler.handleResult();
                                handleTaskRunnerSuccess(BuyerEvent.PAYMENT_SENT);
                            },
                            (errorMessage) -> {
                                errorMessageHandler.handleErrorMessage(errorMessage);
                                handleTaskRunnerFault(errorMessage);
                            });
                    taskRunner.addTasks(
                            ApplyFilter.class,
                            MakerVerifyTakerFeePayment.class,
                            BuyerSignPayoutTx.class,
                            BuyerSetupPayoutTxListener.class,
                            BuyerSendCounterCurrencyTransferStartedMessage.class
                    );
                    taskRunner.run();
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message Payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PayoutTxPublishedMessage message, NodeAddress peer) {
        fromAny(Trade.Phase.FIAT_SENT, Trade.Phase.PAYOUT_PUBLISHED)
                .on(message)
                .from(peer)
                .process(() -> {
                    processModel.setTradeMessage(message);
                    processModel.setTempTradingPeerNodeAddress(peer);

                    TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsMakerTrade,
                            () -> handleTaskRunnerSuccess(message),
                            errorMessage -> handleTaskRunnerFault(message, errorMessage));

                    taskRunner.addTasks(
                            BuyerProcessPayoutTxPublishedMessage.class
                    );
                    taskRunner.run();
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage message, NodeAddress peer) {
        super.doHandleDecryptedMessage(message, peer);

        log.info("Received {} from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peer, message.getTradeId(), message.getUid());

        if (message instanceof DelayedPayoutTxSignatureRequest) {
            handle((DelayedPayoutTxSignatureRequest) message, peer);
        } else if (message instanceof DepositTxAndDelayedPayoutTxMessage) {
            handle((DepositTxAndDelayedPayoutTxMessage) message, peer);
        } else if (message instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) message, peer);
        }
    }
}

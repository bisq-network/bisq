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
    public void doApplyMailboxTradeMessage(TradeMessage message, NodeAddress peerNodeAddress) {
        super.doApplyMailboxTradeMessage(message, peerNodeAddress);

        if (message instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) message, peerNodeAddress);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        expectedPhase(Trade.Phase.INIT)
                .on(TakerEvent.TAKE_OFFER)
                .withTimeout(30)
                .addTasks(
                        ApplyFilter.class,
                        TakerVerifyMakerFeePayment.class,
                        CreateTakerFeeTx.class, //
                        SellerAsTakerCreatesDepositTxInputs.class,
                        TakerSendInputsForDepositTxRequest.class
                ).runTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming messages Take offer process
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(InputsForDepositTxResponse message, NodeAddress peer) {
        expectedPhase(Trade.Phase.INIT)
                .on(message)
                .from(peer)
                .withTimeout(30)
                .addTasks(
                        TakerProcessesInputsForDepositTxResponse.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class,
                        TakerVerifyAndSignContract.class,
                        TakerPublishFeeTx.class,
                        SellerAsTakerSignsDepositTx.class,
                        SellerCreatesDelayedPayoutTx.class,
                        SellerSendDelayedPayoutTxSignatureRequest.class
                )
                .runTasks();
    }

    private void handle(DelayedPayoutTxSignatureResponse message, NodeAddress peer) {
        expectedPhase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .on(message)
                .from(peer)
                .addTasks(
                        SellerProcessDelayedPayoutTxSignatureResponse.class,
                        SellerSignsDelayedPayoutTx.class,
                        SellerFinalizesDelayedPayoutTx.class,
                        SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                        SellerPublishesDepositTx.class,
                        PublishTradeStatistics.class
                )
                .runTasks();
        /*
                .addTasks(() -> {
                    // We stop timeout here and don't start a new one as the
                    // SellerSendsDepositTxAndDelayedPayoutTxMessage repeats the send the message and has it's own
                    // timeout if it never succeeds.
                    stopTimeout();

                    TradeTaskRunner taskRunner = new TradeTaskRunner(sellerAsTakerTrade,
                            () -> {

                                handleTaskRunnerSuccess(message);
                            },
                            errorMessage -> handleTaskRunnerFault(message, errorMessage));

                    taskRunner.addTasks(
                            SellerProcessDelayedPayoutTxSignatureResponse.class,
                            SellerSignsDelayedPayoutTx.class,
                            SellerFinalizesDelayedPayoutTx.class,
                            SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                            SellerPublishesDepositTx.class,
                            PublishTradeStatistics.class
                    );
                    taskRunner.run();
                    processModel.witnessDebugLog(sellerAsTakerTrade);
                });*/
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message when buyer has clicked payment started button
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(CounterCurrencyTransferStartedMessage message, NodeAddress peer) {
        expectedPhase(Trade.Phase.DEPOSIT_CONFIRMED)
                .on(message)
                .from(peer)
                .preCondition(trade.getPayoutTx() == null,
                        () -> {
                            log.warn("We received a CounterCurrencyTransferStartedMessage but we have already created the payout tx " +
                                    "so we ignore the message. This can happen if the ACK message to the peer did not " +
                                    "arrive and the peer repeats sending us the message. We send another ACK msg.");
                            sendAckMessage(message, true, null);
                            processModel.removeMailboxMessageAfterProcessing(trade);
                        })
                .addTasks(
                        SellerProcessCounterCurrencyTransferStartedMessage.class,
                        ApplyFilter.class,
                        TakerVerifyMakerFeePayment.class
                )
                .runTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        SellerEvent event = SellerEvent.PAYMENT_RECEIVED;
        expectedPhase(Trade.Phase.FIAT_SENT)
                .on(event)
                .preCondition(!wasDisputed())
                .setTaskRunner(new TradeTaskRunner(sellerAsTakerTrade,
                        () -> {
                            resultHandler.handleResult();
                            handleTaskRunnerSuccess(event);
                        },
                        (errorMessage) -> {
                            errorMessageHandler.handleErrorMessage(errorMessage);
                            handleTaskRunnerFault(event, errorMessage);
                        }))
                .addTasks(
                        ApplyFilter.class,
                        TakerVerifyMakerFeePayment.class,
                        SellerSignAndFinalizePayoutTx.class,
                        SellerBroadcastPayoutTx.class,
                        SellerSendPayoutTxPublishedMessage.class //TODO add repeated msg send, check UI
                ).runTasks();
        //sellerAsTakerTrade.setState(Trade.State.SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage message, NodeAddress peer) {
        super.doHandleDecryptedMessage(message, peer);

        log.info("Received {} from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peer, message.getTradeId(), message.getUid());

        if (message instanceof InputsForDepositTxResponse) {
            handle((InputsForDepositTxResponse) message, peer);
        } else if (message instanceof DelayedPayoutTxSignatureResponse) {
            handle((DelayedPayoutTxSignatureResponse) message, peer);
        } else if (message instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) message, peer);
        }
    }
}

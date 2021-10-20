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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.trade.model.bisq_v1.BuyerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bisq_v1.messages.DelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.messages.PayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerProcessDepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSendsShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSetupDepositTxListener;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSetupPayoutTxListener;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSignPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerVerifiesFinalDelayedPayoutTx;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BuyerProtocol extends DisputeProtocol {
    enum BuyerEvent implements FluentProtocol.Event {
        STARTUP,
        PAYMENT_SENT
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerProtocol(BuyerTrade trade) {
        super(trade);
    }

    @Override
    protected void onInitialized() {
        super.onInitialized();
        // We get called the constructor with any possible state and phase. As we don't want to log an error for such
        // cases we use the alternative 'given' method instead of 'expect'.
        given(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .with(BuyerEvent.STARTUP))
                .setup(tasks(BuyerSetupDepositTxListener.class))
                .executeTasks();

        given(anyPhase(Trade.Phase.FIAT_SENT, Trade.Phase.FIAT_RECEIVED)
                .with(BuyerEvent.STARTUP))
                .setup(tasks(BuyerSetupPayoutTxListener.class))
                .executeTasks();

        given(anyPhase(Trade.Phase.FIAT_SENT, Trade.Phase.FIAT_RECEIVED)
                .anyState(Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG,
                        Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG)
                .with(BuyerEvent.STARTUP))
                .setup(tasks(BuyerSendCounterCurrencyTransferStartedMessage.class))
                .executeTasks();
    }

    @Override
    public void onMailboxMessage(TradeMessage message, NodeAddress peer) {
        super.onMailboxMessage(message, peer);

        if (message instanceof DepositTxAndDelayedPayoutTxMessage) {
            handle((DepositTxAndDelayedPayoutTxMessage) message, peer);
        } else if (message instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) message, peer);
        }
    }

    protected abstract void handle(DelayedPayoutTxSignatureRequest message, NodeAddress peer);

    // The DepositTxAndDelayedPayoutTxMessage is a mailbox message. Earlier we used only the deposit tx which can
    // be set also when received by the network once published by the peer so that message was not mandatory and could
    // have arrived as mailbox message.
    // Now we send the delayed payout tx as well and with that this message is mandatory for continuing the protocol.
    // We do not support mailbox message handling during the take offer process as it is expected that both peers
    // are online.
    // For backward compatibility and extra resilience we still keep DepositTxAndDelayedPayoutTxMessage as a
    // mailbox message but the stored in mailbox case is not expected and the seller would try to send the message again
    // in the hope to reach the buyer directly in case of network issues.
    protected void handle(DepositTxAndDelayedPayoutTxMessage message, NodeAddress peer) {
        expect(anyPhase(Trade.Phase.TAKER_FEE_PUBLISHED, Trade.Phase.DEPOSIT_PUBLISHED)
                .with(message)
                .from(peer)
                .preCondition(trade.getDepositTx() == null || processModel.getTradePeer().getPaymentAccountPayload() == null,
                        () -> {
                            log.warn("We received a DepositTxAndDelayedPayoutTxMessage but we have already processed the deposit and " +
                                    "delayed payout tx so we ignore the message. This can happen if the ACK message to the peer did not " +
                                    "arrive and the peer repeats sending us the message. We send another ACK msg.");
                            stopTimeout();
                            sendAckMessage(message, true, null);
                            removeMailboxMessageAfterProcessing(message);
                        }))
                .setup(tasks(BuyerProcessDepositTxAndDelayedPayoutTxMessage.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class,
                        BuyerSendsShareBuyerPaymentAccountMessage.class,
                        BuyerVerifiesFinalDelayedPayoutTx.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    stopTimeout();
                                    handleTaskRunnerSuccess(message);
                                },
                                errorMessage -> handleTaskRunnerFault(message, errorMessage))))
                .executeTasks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        BuyerEvent event = BuyerEvent.PAYMENT_SENT;
        expect(phase(Trade.Phase.DEPOSIT_CONFIRMED)
                .with(event)
                .preCondition(trade.confirmPermitted()))
                .setup(tasks(ApplyFilter.class,
                        getVerifyPeersFeePaymentClass(),
                        BuyerSignPayoutTx.class,
                        BuyerSetupPayoutTxListener.class,
                        BuyerSendCounterCurrencyTransferStartedMessage.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    resultHandler.handleResult();
                                    handleTaskRunnerSuccess(event);
                                },
                                (errorMessage) -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(event, errorMessage);
                                })))
                .run(() -> {
                    trade.setState(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED);
                    processModel.getTradeManager().requestPersistence();
                })
                .executeTasks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message Payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(PayoutTxPublishedMessage message, NodeAddress peer) {
        expect(anyPhase(Trade.Phase.FIAT_SENT, Trade.Phase.PAYOUT_PUBLISHED)
                .with(message)
                .from(peer))
                .setup(tasks(BuyerProcessPayoutTxPublishedMessage.class))
                .executeTasks();

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        super.onTradeMessage(message, peer);

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

    abstract protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass();
}

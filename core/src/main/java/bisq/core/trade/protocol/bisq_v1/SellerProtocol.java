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

import bisq.core.trade.model.bisq_v1.SellerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bisq_v1.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.messages.DelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v1.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerBroadcastPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerFinalizesDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerProcessCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerProcessDelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerProcessShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesTradeStatistics;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSendPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSendsDepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSignAndFinalizePayoutTx;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SellerProtocol extends DisputeProtocol {
    enum SellerEvent implements FluentProtocol.Event {
        STARTUP,
        PAYMENT_RECEIVED
    }

    public SellerProtocol(SellerTrade trade) {
        super(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMailboxMessage(TradeMessage message, NodeAddress peerNodeAddress) {
        super.onMailboxMessage(message, peerNodeAddress);

        if (message instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) message, peerNodeAddress);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming messages
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(DelayedPayoutTxSignatureResponse message, NodeAddress peer) {
        expect(phase(Trade.Phase.TAKER_FEE_PUBLISHED)
                .with(message)
                .from(peer))
                .setup(tasks(SellerProcessDelayedPayoutTxSignatureResponse.class,
                        SellerFinalizesDelayedPayoutTx.class,
                        SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                        SellerPublishesDepositTx.class,
                        SellerPublishesTradeStatistics.class))
                .executeTasks();
    }

    protected void handle(ShareBuyerPaymentAccountMessage message, NodeAddress peer) {
        expect(anyPhase(Trade.Phase.TAKER_FEE_PUBLISHED, Trade.Phase.DEPOSIT_PUBLISHED, Trade.Phase.DEPOSIT_CONFIRMED)
                .with(message)
                .from(peer))
                .setup(tasks(SellerProcessShareBuyerPaymentAccountMessage.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class))
                .run(() -> {
                    // We stop timeout here and don't start a new one as the
                    // SellerSendsDepositTxAndDelayedPayoutTxMessage repeats to send the message and has it's own
                    // timeout if it never succeeds.
                    stopTimeout();
                })
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message when buyer has clicked payment started button
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(CounterCurrencyTransferStartedMessage message, NodeAddress peer) {
        // We are more tolerant with expected phase and allow also DEPOSIT_PUBLISHED as it can be the case
        // that the wallet is still syncing and so the DEPOSIT_CONFIRMED state to yet triggered when we received
        // a mailbox message with CounterCurrencyTransferStartedMessage.
        // TODO A better fix would be to add a listener for the wallet sync state and process
        // the mailbox msg once wallet is ready and trade state set.
        expect(anyPhase(Trade.Phase.DEPOSIT_CONFIRMED, Trade.Phase.DEPOSIT_PUBLISHED)
                .with(message)
                .from(peer)
                .preCondition(trade.getPayoutTx() == null,
                        () -> {
                            log.warn("We received a CounterCurrencyTransferStartedMessage but we have already created the payout tx " +
                                    "so we ignore the message. This can happen if the ACK message to the peer did not " +
                                    "arrive and the peer repeats sending us the message. We send another ACK msg.");
                            sendAckMessage(message, true, null);
                            removeMailboxMessageAfterProcessing(message);
                        }))
                .setup(tasks(
                        SellerProcessCounterCurrencyTransferStartedMessage.class,
                        ApplyFilter.class,
                        getVerifyPeersFeePaymentClass()))
                .executeTasks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        SellerEvent event = SellerEvent.PAYMENT_RECEIVED;
        expect(anyPhase(Trade.Phase.FIAT_SENT, Trade.Phase.PAYOUT_PUBLISHED)
                .with(event)
                .preCondition(trade.confirmPermitted()))
                .setup(tasks(
                        ApplyFilter.class,
                        getVerifyPeersFeePaymentClass(),
                        SellerSignAndFinalizePayoutTx.class,
                        SellerBroadcastPayoutTx.class,
                        SellerSendPayoutTxPublishedMessage.class)
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
                    trade.setState(Trade.State.SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT);
                    processModel.getTradeManager().requestPersistence();
                })
                .executeTasks();
    }


    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        super.onTradeMessage(message, peer);

        log.info("Received {} from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peer, message.getTradeId(), message.getUid());

        if (message instanceof DelayedPayoutTxSignatureResponse) {
            handle((DelayedPayoutTxSignatureResponse) message, peer);
        } else if (message instanceof ShareBuyerPaymentAccountMessage) {
            handle((ShareBuyerPaymentAccountMessage) message, peer);
        } else if (message instanceof CounterCurrencyTransferStartedMessage) {
            handle((CounterCurrencyTransferStartedMessage) message, peer);
        }
    }

    abstract protected Class<? extends TradeTask> getVerifyPeersFeePaymentClass();

}

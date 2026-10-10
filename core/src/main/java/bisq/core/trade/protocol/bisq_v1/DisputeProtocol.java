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

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bisq_v1.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.messages.MediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.messages.MediatedPayoutTxSignatureMessage;
import bisq.core.trade.protocol.bisq_v1.messages.PeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.ProcessPeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.arbitration.PublishedDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.arbitration.SendPeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.BroadcastMediatedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.FinalizeMediatedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.ProcessMediatedPayoutSignatureMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.ProcessMediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.SendMediatedPayoutSignatureMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.SendMediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.SetupMediatedPayoutTxListener;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.SignMediatedPayoutTx;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.TradeValidation.checkTradeId;

@Slf4j
public class DisputeProtocol extends TradeProtocol {

    protected Trade trade;
    protected final ProcessModel processModel;

    enum DisputeEvent implements FluentProtocol.Event {
        MEDIATION_RESULT_ACCEPTED,
        MEDIATION_RESULT_REJECTED,
        ARBITRATION_REQUESTED
    }

    // Phases in which a trader accepts the MediatedPayoutTxPublishedMessage. Both traders can publish the
    // mediated payout, so the message can arrive after our own payout was published. We keep our payout then.
    @VisibleForTesting
    public static final Trade.Phase[] MEDIATED_PAYOUT_TX_PUBLISHED_MSG_PHASES = {
            Trade.Phase.DEPOSIT_CONFIRMED,
            Trade.Phase.FIAT_SENT,
            Trade.Phase.FIAT_RECEIVED,
            Trade.Phase.PAYOUT_PUBLISHED
    };

    public DisputeProtocol(Trade trade) {
        super(trade);
        this.trade = trade;
        this.processModel = trade.getProcessModel();
    }

    @Override
    protected void onInitialized() {
        super.onInitialized();
        processModel.applyPaymentAccount(trade);
        maybeFinalizeMediatedPayout();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TradeProtocol implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onAckMessage(AckMessage ackMessage, NodeAddress peer) {
        // We handle the ack for CounterCurrencyTransferStartedMessage and DepositTxAndDelayedPayoutTxMessage
        // as we support automatic re-send of the msg in case it was not ACKed after a certain time
        if (ackMessage.getSourceMsgClassName().equals(CounterCurrencyTransferStartedMessage.class.getSimpleName())) {
            processModel.setPaymentStartedAckMessage(ackMessage);
        } else if (ackMessage.getSourceMsgClassName().equals(DepositTxAndDelayedPayoutTxMessage.class.getSimpleName())) {
            processModel.setDepositTxSentAckMessage(ackMessage);
        }

        if (ackMessage.isSuccess()) {
            log.info("Received AckMessage for {} from {} with tradeId {} and uid {}",
                    ackMessage.getSourceMsgClassName(), peer, tradeModel.getId(), ackMessage.getSourceUid());
        } else {
            log.warn("Received AckMessage with error state for {} from {} with tradeId {} and errorMessage={}",
                    ackMessage.getSourceMsgClassName(), peer, tradeModel.getId(), ackMessage.getErrorMessage());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction: Trader accepts mediation result
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Trader has not yet received the peer's signature but has clicked the accept button.
    public void onAcceptMediationResult(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        DisputeEvent event = DisputeEvent.MEDIATION_RESULT_ACCEPTED;
        expect(anyPhase(Trade.Phase.DEPOSIT_CONFIRMED,
                Trade.Phase.FIAT_SENT,
                Trade.Phase.FIAT_RECEIVED)
                .with(event)
                .preCondition(
                        (trade.getProcessModel().getTradePeer().getMediatedPayoutTxSignature() == null) ||
                        (trade.getPayoutTx() == null),
                        () -> errorMessageHandler.handleErrorMessage("We either have received already the signature from the peer or payout tx is already published."))
                )
                .setup(tasks(ApplyFilter.class,
                        SignMediatedPayoutTx.class,
                        SendMediatedPayoutSignatureMessage.class,
                        SetupMediatedPayoutTxListener.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    resultHandler.handleResult();
                                    handleTaskRunnerSuccess(event);
                                },
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(event, errorMessage);
                                })))
                .executeTasks();
    }

    // Trader has already received the peer's signature and has clicked the accept button as well.
    public void onFinalizeMediationResultPayout(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        DisputeEvent event = DisputeEvent.MEDIATION_RESULT_ACCEPTED;
        expect(anyPhase(Trade.Phase.DEPOSIT_CONFIRMED,
                Trade.Phase.FIAT_SENT,
                Trade.Phase.FIAT_RECEIVED)
                .with(event)
                .preCondition(trade.getPayoutTx() == null,
                        () -> errorMessageHandler.handleErrorMessage("Payout tx is already published.")))
                .setup(tasks(ApplyFilter.class,
                        SignMediatedPayoutTx.class,
                        FinalizeMediatedPayoutTx.class,
                        BroadcastMediatedPayoutTx.class,
                        SendMediatedPayoutTxPublishedMessage.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    resultHandler.handleResult();
                                    handleTaskRunnerSuccess(event);
                                },
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(event, errorMessage);
                                })))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mediation: incoming message
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(MediatedPayoutTxSignatureMessage message, NodeAddress peer) {
        expect(anyPhase(Trade.Phase.DEPOSIT_CONFIRMED,
                Trade.Phase.FIAT_SENT,
                Trade.Phase.FIAT_RECEIVED)
                .with(message)
                .from(peer))
                .setup(tasks(ProcessMediatedPayoutSignatureMessage.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    handleTaskRunnerSuccess(message);
                                    maybeFinalizeMediatedPayout();
                                },
                                errorMessage -> handleTaskRunnerFault(message, errorMessage))))
                .executeTasks();
    }

    protected void handle(MediatedPayoutTxPublishedMessage message, NodeAddress peer) {
        expect(anyPhase(MEDIATED_PAYOUT_TX_PUBLISHED_MSG_PHASES)
                .with(message)
                .from(peer))
                .setup(tasks(ProcessMediatedPayoutTxPublishedMessage.class))
                .executeTasks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mediation: both traders have signed
    ///////////////////////////////////////////////////////////////////////////////////////////

    // A trader who has signed the mediated payout and has the peer's signature publishes it. The peer's
    // signature can arrive after our own acceptance, and a trade can be in that state at startup. The
    // check runs after the current event is handled, at startup after the startup tasks of the trade.
    private void maybeFinalizeMediatedPayout() {
        UserThread.execute(() -> {
            if (!isMediatedPayoutReadyToFinalize(trade)) {
                return;
            }

            log.info("We have both signatures of the mediated payout and publish it. tradeId={}", trade.getId());
            onFinalizeMediationResultPayout(() -> {
                        if (trade.getPayoutTx() != null) {
                            processModel.getTradeManager().closeDisputedTrade(trade.getId(),
                                    Trade.DisputeState.MEDIATION_CLOSED);
                        }
                    },
                    errorMessage -> log.warn("Publishing the mediated payout failed. tradeId={}, errorMessage={}",
                            trade.getId(), errorMessage));
        });
    }

    @VisibleForTesting
    public static boolean isMediatedPayoutReadyToFinalize(Trade trade) {
        ProcessModel processModel = trade.getProcessModel();
        return trade.getDisputeState() == Trade.DisputeState.MEDIATION_CLOSED &&
                trade.getPayoutTx() == null &&
                processModel.getMediatedPayoutTxSignature() != null &&
                processModel.getTradePeer().getMediatedPayoutTxSignature() != null;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delayed payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPublishDelayedPayoutTx(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        DisputeEvent event = DisputeEvent.ARBITRATION_REQUESTED;
        expect(anyPhase(Trade.Phase.DEPOSIT_CONFIRMED,
                Trade.Phase.FIAT_SENT,
                Trade.Phase.FIAT_RECEIVED)
                .with(event)
                .preCondition(trade.getDelayedPayoutTx() != null))
                .setup(tasks(PublishedDelayedPayoutTx.class,
                        SendPeerPublishedDelayedPayoutTxMessage.class)
                        .using(new TradeTaskRunner(trade,
                                () -> {
                                    resultHandler.handleResult();
                                    handleTaskRunnerSuccess(event);
                                },
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(event, errorMessage);
                                })))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peer has published the delayed payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PeerPublishedDelayedPayoutTxMessage message, NodeAddress peer) {
        expect(anyPhase(Trade.Phase.DEPOSIT_CONFIRMED,
                Trade.Phase.FIAT_SENT,
                Trade.Phase.FIAT_RECEIVED)
                .with(message)
                .from(peer))
                .setup(tasks(ProcessPeerPublishedDelayedPayoutTxMessage.class))
                .executeTasks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        checkTradeId(processModel.getOfferId(), message);

        if (message instanceof MediatedPayoutTxSignatureMessage) {
            handle((MediatedPayoutTxSignatureMessage) message, peer);
        } else if (message instanceof MediatedPayoutTxPublishedMessage) {
            handle((MediatedPayoutTxPublishedMessage) message, peer);
        } else if (message instanceof PeerPublishedDelayedPayoutTxMessage) {
            handle((PeerPublishedDelayedPayoutTxMessage) message, peer);
        }
    }

    @Override
    protected void onMailboxMessage(TradeMessage message, NodeAddress peer) {
        checkTradeId(processModel.getOfferId(), message);

        super.onMailboxMessage(message, peer);
        if (message instanceof MediatedPayoutTxSignatureMessage) {
            handle((MediatedPayoutTxSignatureMessage) message, peer);
        } else if (message instanceof MediatedPayoutTxPublishedMessage) {
            handle((MediatedPayoutTxPublishedMessage) message, peer);
        } else if (message instanceof PeerPublishedDelayedPayoutTxMessage) {
            handle((PeerPublishedDelayedPayoutTxMessage) message, peer);
        }
    }
}

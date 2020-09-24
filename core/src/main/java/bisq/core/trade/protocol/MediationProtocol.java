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

import bisq.core.trade.Trade;
import bisq.core.trade.messages.MediatedPayoutTxPublishedMessage;
import bisq.core.trade.messages.MediatedPayoutTxSignatureMessage;
import bisq.core.trade.messages.PeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.ProcessPeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.protocol.tasks.mediation.BroadcastMediatedPayoutTx;
import bisq.core.trade.protocol.tasks.mediation.FinalizeMediatedPayoutTx;
import bisq.core.trade.protocol.tasks.mediation.ProcessMediatedPayoutSignatureMessage;
import bisq.core.trade.protocol.tasks.mediation.ProcessMediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.mediation.SendMediatedPayoutSignatureMessage;
import bisq.core.trade.protocol.tasks.mediation.SendMediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.mediation.SetupMediatedPayoutTxListener;
import bisq.core.trade.protocol.tasks.mediation.SignMediatedPayoutTx;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MediationProtocol extends TradeProtocol {

    enum DisputeEvent implements TradeProtocol.Event {
        MEDIATION_RESULT_ACCEPTED,
        MEDIATION_RESULT_REJECTED
    }

    public MediationProtocol(Trade trade) {
        super(trade);
    }

    protected boolean wasDisputed() {
        return trade.getDisputeState() != Trade.DisputeState.NO_DISPUTE;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // User interaction: Trader accepts mediation result
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Trader has not yet received the peer's signature but has clicked the accept button.
    public void onAcceptMediationResult(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.getProcessModel().getTradingPeer().getMediatedPayoutTxSignature() != null) {
            errorMessageHandler.handleErrorMessage("We have received already the signature from the peer.");
            return;
        }
        DisputeEvent event = DisputeEvent.MEDIATION_RESULT_ACCEPTED;
        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> {
                    resultHandler.handleResult();
                    handleTaskRunnerSuccess(event);
                },
                (errorMessage) -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(event, errorMessage);
                });
        taskRunner.addTasks(
                ApplyFilter.class,
                SignMediatedPayoutTx.class,
                SendMediatedPayoutSignatureMessage.class,
                SetupMediatedPayoutTxListener.class
        );
        taskRunner.run();
    }


    // Trader has already received the peer's signature and has clicked the accept button as well.
    public void onFinalizeMediationResultPayout(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.getPayoutTx() != null) {
            errorMessageHandler.handleErrorMessage("Payout tx is already published.");
            return;
        }

        DisputeEvent event = DisputeEvent.MEDIATION_RESULT_ACCEPTED;
        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> {
                    resultHandler.handleResult();
                    handleTaskRunnerSuccess(event);
                },
                (errorMessage) -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(event, errorMessage);
                });
        taskRunner.addTasks(
                ApplyFilter.class,
                SignMediatedPayoutTx.class,
                FinalizeMediatedPayoutTx.class,
                BroadcastMediatedPayoutTx.class,
                SendMediatedPayoutTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mediation: incoming message
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(MediatedPayoutTxSignatureMessage message, NodeAddress peer) {
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerNodeAddress(peer);

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(message),
                errorMessage -> handleTaskRunnerFault(message, errorMessage));

        taskRunner.addTasks(
                ProcessMediatedPayoutSignatureMessage.class
        );
        taskRunner.run();
    }

    protected void handle(MediatedPayoutTxPublishedMessage message, NodeAddress peer) {
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerNodeAddress(peer);

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(message),
                errorMessage -> handleTaskRunnerFault(message, errorMessage));

        taskRunner.addTasks(
                ProcessMediatedPayoutTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peer has published the delayed payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PeerPublishedDelayedPayoutTxMessage message, NodeAddress peer) {
        processModel.setTradeMessage(message);
        processModel.setTempTradingPeerNodeAddress(peer);

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(message),
                errorMessage -> handleTaskRunnerFault(message, errorMessage));

        taskRunner.addTasks(
                ProcessPeerPublishedDelayedPayoutTxMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage message, NodeAddress peer) {
        if (message instanceof MediatedPayoutTxSignatureMessage) {
            handle((MediatedPayoutTxSignatureMessage) message, peer);
        } else if (message instanceof MediatedPayoutTxPublishedMessage) {
            handle((MediatedPayoutTxPublishedMessage) message, peer);
        } else if (message instanceof PeerPublishedDelayedPayoutTxMessage) {
            handle((PeerPublishedDelayedPayoutTxMessage) message, peer);
        }
    }

    @Override
    protected void doApplyMailboxTradeMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress) {
        if (tradeMessage instanceof MediatedPayoutTxSignatureMessage) {
            handle((MediatedPayoutTxSignatureMessage) tradeMessage, peerNodeAddress);
        } else if (tradeMessage instanceof MediatedPayoutTxPublishedMessage) {
            handle((MediatedPayoutTxPublishedMessage) tradeMessage, peerNodeAddress);
        } else if (tradeMessage instanceof PeerPublishedDelayedPayoutTxMessage) {
            handle((PeerPublishedDelayedPayoutTxMessage) tradeMessage, peerNodeAddress);
        }
    }
}

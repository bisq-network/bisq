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
import bisq.core.trade.TradeManager;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.messages.TradeMessage;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.taskrunner.Task;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class TradeProtocol implements DecryptedDirectMessageListener {

    protected final ProcessModel processModel;
    protected Trade trade;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeProtocol(Trade trade) {
        this.trade = trade;
        this.processModel = trade.getProcessModel();

        if (!trade.isWithdrawn()) {
            processModel.getP2PService().addDecryptedDirectMessageListener(this);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWithdrawCompleted() {
        cleanup();
    }

    public void applyMailboxMessage(DecryptedMessageWithPubKey message) {
        if (isPubKeyValid(message)) {
            NetworkEnvelope networkEnvelope = message.getNetworkEnvelope();
            if (networkEnvelope instanceof MailboxMessage &&
                    networkEnvelope instanceof TradeMessage) {
                processModel.setDecryptedMessageWithPubKey(message);
                TradeMessage tradeMessage = (TradeMessage) networkEnvelope;
                NodeAddress peerNodeAddress = ((MailboxMessage) networkEnvelope).getSenderNodeAddress();
                onMailboxMessage(tradeMessage, peerNodeAddress);
            }
        }
    }

    protected void onMailboxMessage(TradeMessage message, NodeAddress peerNodeAddress) {
        log.info("Received {} as MailboxMessage from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peerNodeAddress, message.getTradeId(), message.getUid());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DecryptedDirectMessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDirectMessage(DecryptedMessageWithPubKey message, NodeAddress peer) {
        if (isPubKeyValid(message)) {
            NetworkEnvelope networkEnvelope = message.getNetworkEnvelope();
            if (networkEnvelope instanceof TradeMessage) {
                onTradeMessage((TradeMessage) networkEnvelope, peer);
            } else if (networkEnvelope instanceof AckMessage) {
                onAckMessage((AckMessage) networkEnvelope, peer);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void onTradeMessage(TradeMessage message, NodeAddress peer);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // FluentProtocol
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected FluentProtocol given(FluentProtocol.Condition condition) {
        return new FluentProtocol(this).condition(condition);
    }

    protected FluentProtocol.Condition phase(Trade.Phase expectedPhase) {
        return new FluentProtocol.Condition(trade).phase(expectedPhase);
    }

    protected FluentProtocol.Condition anyPhase(Trade.Phase... expectedPhases) {
        return new FluentProtocol.Condition(trade).anyPhase(expectedPhases);
    }

    @SafeVarargs
    public final FluentProtocol.Setup tasks(Class<? extends Task<Trade>>... tasks) {
        return new FluentProtocol.Setup(this, trade).tasks(tasks);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ACK msg
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAckMessage(AckMessage ackMessage, NodeAddress peer) {
        if (ackMessage.getSourceType() == AckMessageSourceType.TRADE_MESSAGE &&
                ackMessage.getSourceId().equals(trade.getId())) {
            // We handle the ack for CounterCurrencyTransferStartedMessage and DepositTxAndDelayedPayoutTxMessage
            // as we support automatic re-send of the msg in case it was not ACKed after a certain time
            if (ackMessage.getSourceMsgClassName().equals(CounterCurrencyTransferStartedMessage.class.getSimpleName())) {
                processModel.setPaymentStartedAckMessage(ackMessage);
            } else if (ackMessage.getSourceMsgClassName().equals(DepositTxAndDelayedPayoutTxMessage.class.getSimpleName())) {
                processModel.setDepositTxSentAckMessage(ackMessage);
            }

            if (ackMessage.isSuccess()) {
                log.info("Received AckMessage for {} from {} with tradeId {} and uid {}",
                        ackMessage.getSourceMsgClassName(), peer, trade.getId(), ackMessage.getSourceUid());
            } else {
                log.warn("Received AckMessage with error state for {} from {} with tradeId {} and errorMessage={}",
                        ackMessage.getSourceMsgClassName(), peer, trade.getId(), ackMessage.getErrorMessage());
            }
        }
    }

    protected void sendAckMessage(TradeMessage message, boolean result, @Nullable String errorMessage) {
        String tradeId = message.getTradeId();
        String sourceUid = message.getUid();
        AckMessage ackMessage = new AckMessage(processModel.getMyNodeAddress(),
                AckMessageSourceType.TRADE_MESSAGE,
                message.getClass().getSimpleName(),
                sourceUid,
                tradeId,
                result,
                errorMessage);
        // If there was an error during offer verification, the tradingPeerNodeAddress of the trade might not be set yet.
        // We can find the peer's node address in the processModel's tempTradingPeerNodeAddress in that case.
        NodeAddress peer = trade.getTradingPeerNodeAddress() != null ?
                trade.getTradingPeerNodeAddress() :
                processModel.getTempTradingPeerNodeAddress();
        log.info("Send AckMessage for {} to peer {}. tradeId={}, sourceUid={}",
                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid);
        processModel.getP2PService().sendEncryptedMailboxMessage(
                peer,
                processModel.getTradingPeer().getPubKeyRing(),
                ackMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("AckMessage for {} arrived at peer {}. tradeId={}, sourceUid={}",
                                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid);
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("AckMessage for {} stored in mailbox for peer {}. tradeId={}, sourceUid={}",
                                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid);
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("AckMessage for {} failed. Peer {}. tradeId={}, sourceUid={}, errorMessage={}",
                                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid, errorMessage);
                    }
                }
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Timeout
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void startTimeout(long timeoutSec) {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.error("Timeout reached. TradeID={}, state={}, timeoutSec={}",
                    trade.getId(), trade.stateProperty().get(), timeoutSec);
            trade.setErrorMessage("Timeout reached. Protocol did not complete in " + timeoutSec + " sec.");
            cleanupTradeOnFault();
        }, timeoutSec);
    }

    protected void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Task runner
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handleTaskRunnerSuccess(TradeMessage message) {
        handleTaskRunnerSuccess(message, message.getClass().getSimpleName());
    }

    protected void handleTaskRunnerSuccess(FluentProtocol.Event event) {
        handleTaskRunnerSuccess(null, event.name());
    }

    protected void handleTaskRunnerFault(TradeMessage message, String errorMessage) {
        handleTaskRunnerFault(message, message.getClass().getSimpleName(), errorMessage);
    }

    protected void handleTaskRunnerFault(FluentProtocol.Event event, String errorMessage) {
        handleTaskRunnerFault(null, event.name(), errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Validation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isPubKeyValid(DecryptedMessageWithPubKey message) {
        // We can only validate the peers pubKey if we have it already. If we are the taker we get it from the offer
        // Otherwise it depends on the state of the trade protocol if we have received the peers pubKeyRing already.
        PubKeyRing peersPubKeyRing = processModel.getTradingPeer().getPubKeyRing();
        boolean isValid = true;
        if (peersPubKeyRing != null &&
                !message.getSignaturePubKey().equals(peersPubKeyRing.getSignaturePubKey())) {
            isValid = false;
            log.error("SignaturePubKey in message does not match the SignaturePubKey we have set for our trading peer.");
        }
        return isValid;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleTaskRunnerSuccess(@Nullable TradeMessage message, String source) {
        log.info("TaskRunner successfully completed. Triggered from {}", source);
        if (message != null) {
            sendAckMessage(message, true, null);
        }
    }

    private void handleTaskRunnerFault(@Nullable TradeMessage message, String source, String errorMessage) {
        log.error("Task runner failed with error {}. Triggered from {}", errorMessage, source);

        if (message != null) {
            sendAckMessage(message, false, errorMessage);
        }
        cleanupTradeOnFault();
    }


    private void cleanup() {
        stopTimeout();
        processModel.getP2PService().removeDecryptedDirectMessageListener(this);
    }

    //todo
    private void cleanupTradeOnFault() {
        cleanup();

        log.warn("cleanupTradableOnFault tradeState={}", trade.getState());
        TradeManager tradeManager = processModel.getTradeManager();
        if (trade.isInPreparation()) {
            // no funds left. we just clean up the trade list
            tradeManager.removePreparedTrade(trade);
        } else if (!trade.isFundsLockedIn()) {
            // No deposit tx published yet
            if (processModel.getPreparedDepositTx() == null) {
                if (trade.isTakerFeePublished()) {
                    tradeManager.addTradeToFailedTrades(trade);
                } else {
                    tradeManager.addTradeToClosedTrades(trade);
                }
            } else {
                log.error("We have already sent the prepared deposit tx to the peer but we did not received the reply " +
                        "about the deposit tx nor saw it in the network. tradeId={}, tradeState={}", trade.getId(), trade.getState());
            }
        }
    }
}

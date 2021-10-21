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
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.mailbox.MailboxMessage;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.messaging.DecryptedMailboxListener;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.taskrunner.Task;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class TradeProtocol implements DecryptedDirectMessageListener, DecryptedMailboxListener {

    @Getter
    protected final ProtocolModel<? extends TradePeer> protocolModel;
    protected final TradeModel tradeModel;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeProtocol(TradeModel tradeModel) {
        this.tradeModel = tradeModel;
        this.protocolModel = tradeModel.getTradeProtocolModel();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(Provider serviceProvider, TradeManager tradeManager, Offer offer) {
        protocolModel.applyTransient(serviceProvider, tradeManager, offer);
        onInitialized();
    }

    protected void onInitialized() {
        if (!tradeModel.isCompleted()) {
            protocolModel.getP2PService().addDecryptedDirectMessageListener(this);
        }

        MailboxMessageService mailboxMessageService = protocolModel.getP2PService().getMailboxMessageService();
        // We delay a bit here as the tradeModel gets updated from the wallet to update the tradeModel
        // state (deposit confirmed) and that happens after our method is called.
        // TODO To fix that in a better way we would need to change the order of some routines
        // from the TradeManager, but as we are close to a release I dont want to risk a bigger
        // change and leave that for a later PR
        UserThread.runAfter(() -> {
            mailboxMessageService.addDecryptedMailboxListener(this);
            handleMailboxCollection(mailboxMessageService.getMyDecryptedMailboxMessages());
        }, 100, TimeUnit.MILLISECONDS);
    }

    public void onWithdrawCompleted() {
        cleanup();
    }

    protected void onMailboxMessage(TradeMessage message, NodeAddress peerNodeAddress) {
        log.info("Received {} as MailboxMessage from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peerNodeAddress, message.getTradeId(), message.getUid());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DecryptedDirectMessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDirectMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, NodeAddress peer) {
        NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
        if (!isMyMessage(networkEnvelope)) {
            return;
        }

        if (!isPubKeyValid(decryptedMessageWithPubKey)) {
            return;
        }

        if (networkEnvelope instanceof TradeMessage) {
            onTradeMessage((TradeMessage) networkEnvelope, peer);
        } else if (networkEnvelope instanceof AckMessage) {
            onAckMessage((AckMessage) networkEnvelope, peer);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DecryptedMailboxListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMailboxMessageAdded(DecryptedMessageWithPubKey decryptedMessageWithPubKey, NodeAddress peer) {
        handleMailboxCollection(Collections.singletonList(decryptedMessageWithPubKey));
    }

    private void handleMailboxCollection(Collection<DecryptedMessageWithPubKey> collection) {
        collection.stream()
                .filter(this::isPubKeyValid)
                .map(DecryptedMessageWithPubKey::getNetworkEnvelope)
                .filter(this::isMyMessage)
                .filter(e -> e instanceof MailboxMessage)
                .map(e -> (MailboxMessage) e)
                .forEach(this::handleMailboxMessage);
    }

    private void handleMailboxMessage(MailboxMessage mailboxMessage) {
        ProtocolModel<? extends TradePeer> protocolModel = tradeModel.getTradeProtocolModel();
        if (mailboxMessage instanceof TradeMessage) {
            TradeMessage tradeMessage = (TradeMessage) mailboxMessage;
            // We only remove here if we have already completed the tradeModel.
            // Otherwise removal is done after successfully applied the task runner.
            if (tradeModel.isCompleted()) {
                protocolModel.getP2PService().getMailboxMessageService().removeMailboxMsg(mailboxMessage);
                log.info("Remove {} from the P2P network as tradeModel is already completed.",
                        tradeMessage.getClass().getSimpleName());
                return;
            }
            onMailboxMessage(tradeMessage, mailboxMessage.getSenderNodeAddress());
        } else if (mailboxMessage instanceof AckMessage) {
            AckMessage ackMessage = (AckMessage) mailboxMessage;
            if (!tradeModel.isCompleted()) {
                // We only apply the msg if we have not already completed the tradeModel
                onAckMessage(ackMessage, mailboxMessage.getSenderNodeAddress());
            }
            // In any case we remove the msg
            protocolModel.getP2PService().getMailboxMessageService().removeMailboxMsg(ackMessage);
            log.info("Remove {} from the P2P network.", ackMessage.getClass().getSimpleName());
        }
    }

    public void removeMailboxMessageAfterProcessing(TradeMessage tradeMessage) {
        if (tradeMessage instanceof MailboxMessage) {
            protocolModel.getP2PService().getMailboxMessageService().removeMailboxMsg((MailboxMessage) tradeMessage);
            log.info("Remove {} from the P2P network.", tradeMessage.getClass().getSimpleName());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void onTradeMessage(TradeMessage message, NodeAddress peer);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // FluentProtocol
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We log an error if condition is not met and call the protocol error handler
    protected FluentProtocol expect(FluentProtocol.Condition condition) {
        return new FluentProtocol(this)
                .condition(condition)
                .resultHandler(result -> {
                    if (!result.isValid()) {
                        log.warn(result.getInfo());
                        handleTaskRunnerFault(null,
                                result.name(),
                                result.getInfo());
                    }
                });
    }

    // We execute only if condition is met but do not log an error.
    protected FluentProtocol given(FluentProtocol.Condition condition) {
        return new FluentProtocol(this)
                .condition(condition);
    }

    protected FluentProtocol.Condition phase(Trade.Phase expectedPhase) {
        return new FluentProtocol.Condition(tradeModel).phase(expectedPhase);
    }

    protected FluentProtocol.Condition anyPhase(Trade.Phase... expectedPhases) {
        return new FluentProtocol.Condition(tradeModel).anyPhase(expectedPhases);
    }

    protected FluentProtocol.Condition preCondition(boolean preCondition) {
        return new FluentProtocol.Condition(tradeModel).preCondition(preCondition);
    }

    @SafeVarargs
    public final FluentProtocol.Setup tasks(Class<? extends Task<TradeModel>>... tasks) {
        return new FluentProtocol.Setup(this, tradeModel).tasks(tasks);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ACK msg
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract protected void onAckMessage(AckMessage ackMessage, NodeAddress peer);

    protected void sendAckMessage(TradeMessage message, boolean result, @Nullable String errorMessage) {
        PubKeyRing peersPubKeyRing = protocolModel.getTradePeer().getPubKeyRing();
        if (peersPubKeyRing == null) {
            log.error("We cannot send the ACK message as peersPubKeyRing is null");
            return;
        }

        String tradeId = message.getTradeId();
        String sourceUid = message.getUid();
        AckMessage ackMessage = new AckMessage(protocolModel.getMyNodeAddress(),
                AckMessageSourceType.TRADE_MESSAGE,
                message.getClass().getSimpleName(),
                sourceUid,
                tradeId,
                result,
                errorMessage);
        // If there was an error during offer verification, the tradingPeerNodeAddress of the tradeModel might not be set yet.
        // We can find the peer's node address in the protocolModel's tempTradingPeerNodeAddress in that case.
        NodeAddress peer = tradeModel.getTradingPeerNodeAddress() != null ?
                tradeModel.getTradingPeerNodeAddress() :
                protocolModel.getTempTradingPeerNodeAddress();
        log.info("Send AckMessage for {} to peer {}. tradeId={}, sourceUid={}",
                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid);
        protocolModel.getP2PService().getMailboxMessageService().sendEncryptedMailboxMessage(
                peer,
                peersPubKeyRing,
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
                    tradeModel.getId(), tradeModel.getTradeState(), timeoutSec);
            tradeModel.setErrorMessage("Timeout reached. Protocol did not complete in " + timeoutSec + " sec.");

            protocolModel.getTradeManager().requestPersistence();
            cleanup();
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
        // Otherwise it depends on the state of the tradeModel protocol if we have received the peers pubKeyRing already.
        PubKeyRing peersPubKeyRing = protocolModel.getTradePeer().getPubKeyRing();
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
        log.info("TaskRunner successfully completed. Triggered from {}, tradeId={}", source, tradeModel.getId());
        if (message != null) {
            sendAckMessage(message, true, null);

            // Once a taskRunner is completed we remove the mailbox message. To not remove it directly at the task
            // adds some resilience in case of minor errors, so after a restart the mailbox message can be applied
            // again.
            removeMailboxMessageAfterProcessing(message);
        }
    }

    void handleTaskRunnerFault(@Nullable TradeMessage message, String source, String errorMessage) {
        log.error("Task runner failed with error {}. Triggered from {}", errorMessage, source);

        if (message != null) {
            sendAckMessage(message, false, errorMessage);
        }
        cleanup();
    }

    private boolean isMyMessage(NetworkEnvelope message) {
        if (message instanceof TradeMessage) {
            TradeMessage tradeMessage = (TradeMessage) message;
            return tradeMessage.getTradeId().equals(tradeModel.getId());
        } else if (message instanceof AckMessage) {
            AckMessage ackMessage = (AckMessage) message;
            return ackMessage.getSourceType() == AckMessageSourceType.TRADE_MESSAGE &&
                    ackMessage.getSourceId().equals(tradeModel.getId());
        } else {
            return false;
        }
    }

    private void cleanup() {
        stopTimeout();
        // We do not remove the decryptedDirectMessageListener as in case of not critical failures we want allow to receive
        // follow-up messages still
    }
}

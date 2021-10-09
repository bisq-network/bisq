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

package bisq.core.support;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.locale.Res;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class SupportManager {
    protected final P2PService p2PService;
    protected final WalletsSetup walletsSetup;
    protected final Map<String, Timer> delayMsgMap = new HashMap<>();
    private final CopyOnWriteArraySet<DecryptedMessageWithPubKey> decryptedMailboxMessageWithPubKeys = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<DecryptedMessageWithPubKey> decryptedDirectMessageWithPubKeys = new CopyOnWriteArraySet<>();
    protected final MailboxMessageService mailboxMessageService;
    private boolean allServicesInitialized;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SupportManager(P2PService p2PService, WalletsSetup walletsSetup) {
        this.p2PService = p2PService;
        mailboxMessageService = p2PService.getMailboxMessageService();

        this.walletsSetup = walletsSetup;

        // We get first the message handler called then the onBootstrapped
        p2PService.addDecryptedDirectMessageListener((decryptedMessageWithPubKey, senderAddress) -> {
            // As decryptedDirectMessageWithPubKeys is a CopyOnWriteArraySet we do not need to check if it was
            // already stored
            decryptedDirectMessageWithPubKeys.add(decryptedMessageWithPubKey);
            tryApplyMessages();
        });
        mailboxMessageService.addDecryptedMailboxListener((decryptedMessageWithPubKey, senderAddress) -> {
            // As decryptedMailboxMessageWithPubKeys is a CopyOnWriteArraySet we do not need to check if it was
            // already stored
            decryptedMailboxMessageWithPubKeys.add(decryptedMessageWithPubKey);
            tryApplyMessages();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void onSupportMessage(SupportMessage networkEnvelope);

    public abstract NodeAddress getPeerNodeAddress(ChatMessage message);

    public abstract PubKeyRing getPeerPubKeyRing(ChatMessage message);

    public abstract SupportType getSupportType();

    public abstract boolean channelOpen(ChatMessage message);

    public abstract List<ChatMessage> getAllChatMessages(String tradeId);

    public abstract void addAndPersistChatMessage(ChatMessage message);

    public abstract void requestPersistence();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates p2pService
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }

    public NodeAddress getMyAddress() {
        return p2PService.getAddress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        allServicesInitialized = true;
    }

    public void tryApplyMessages() {
        if (isReady())
            applyMessages();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void onChatMessage(ChatMessage chatMessage) {
        final String tradeId = chatMessage.getTradeId();
        final String uid = chatMessage.getUid();
        boolean channelOpen = channelOpen(chatMessage);
        if (!channelOpen) {
            log.debug("We got a chatMessage but we don't have a matching chat. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                Timer timer = UserThread.runAfter(() -> onChatMessage(chatMessage), 1);
                delayMsgMap.put(uid, timer);
            } else {
                String msg = "We got a chatMessage after we already repeated to apply the message after a delay. That should never happen. TradeId = " + tradeId;
                log.warn(msg);
            }
            return;
        }

        cleanupRetryMap(uid);
        PubKeyRing receiverPubKeyRing = getPeerPubKeyRing(chatMessage);

        addAndPersistChatMessage(chatMessage);

        // We never get a errorMessage in that method (only if we cannot resolve the receiverPubKeyRing but then we
        // cannot send it anyway)
        if (receiverPubKeyRing != null)
            sendAckMessage(chatMessage, receiverPubKeyRing, true, null);
    }

    private void onAckMessage(AckMessage ackMessage) {
        if (ackMessage.getSourceType() == getAckMessageSourceType()) {
            if (ackMessage.isSuccess()) {
                log.info("Received AckMessage for {} with tradeId {} and uid {}",
                        ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getSourceUid());
            } else {
                log.warn("Received AckMessage with error state for {} with tradeId {} and errorMessage={}",
                        ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getErrorMessage());
            }

            getAllChatMessages(ackMessage.getSourceId()).stream()
                    .filter(msg -> msg.getUid().equals(ackMessage.getSourceUid()))
                    .forEach(msg -> {
                        if (ackMessage.isSuccess())
                            msg.setAcknowledged(true);
                        else
                            msg.setAckError(ackMessage.getErrorMessage());
                    });
            requestPersistence();
        }
    }

    protected abstract AckMessageSourceType getAckMessageSourceType();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send message
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ChatMessage sendChatMessage(ChatMessage message) {
        NodeAddress peersNodeAddress = getPeerNodeAddress(message);
        PubKeyRing receiverPubKeyRing = getPeerPubKeyRing(message);
        if (peersNodeAddress == null || receiverPubKeyRing == null) {
            UserThread.runAfter(() ->
                message.setSendMessageError(Res.get("support.receiverNotKnown")), 1);
        } else {
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());

            mailboxMessageService.sendEncryptedMailboxMessage(peersNodeAddress,
                    receiverPubKeyRing,
                    message,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            message.setArrived(true);
                            requestPersistence();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            message.setStoredInMailbox(true);
                            requestPersistence();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid(), errorMessage);
                            message.setSendMessageError(errorMessage);
                            requestPersistence();
                        }
                    }
            );
        }

        return message;
    }

    protected void sendAckMessage(SupportMessage supportMessage, PubKeyRing peersPubKeyRing,
                                  boolean result, @Nullable String errorMessage) {
        String tradeId = supportMessage.getTradeId();
        String uid = supportMessage.getUid();
        AckMessage ackMessage = new AckMessage(p2PService.getNetworkNode().getNodeAddress(),
                getAckMessageSourceType(),
                supportMessage.getClass().getSimpleName(),
                uid,
                tradeId,
                result,
                errorMessage);
        final NodeAddress peersNodeAddress = supportMessage.getSenderNodeAddress();
        log.info("Send AckMessage for {} to peer {}. tradeId={}, uid={}",
                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, uid);
        mailboxMessageService.sendEncryptedMailboxMessage(
                peersNodeAddress,
                peersPubKeyRing,
                ackMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("AckMessage for {} arrived at peer {}. tradeId={}, uid={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, uid);
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("AckMessage for {} stored in mailbox for peer {}. tradeId={}, uid={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, uid);
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("AckMessage for {} failed. Peer {}. tradeId={}, uid={}, errorMessage={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, uid, errorMessage);
                    }
                }
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected boolean canProcessMessage(SupportMessage message) {
        return message.getSupportType() == getSupportType();
    }

    protected void cleanupRetryMap(String uid) {
        if (delayMsgMap.containsKey(uid)) {
            Timer timer = delayMsgMap.remove(uid);
            if (timer != null)
                timer.stop();
        }
    }

    private boolean isReady() {
        return allServicesInitialized &&
                p2PService.isBootstrapped() &&
                walletsSetup.isDownloadComplete() &&
                walletsSetup.hasSufficientPeersForBroadcast();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyMessages() {
        decryptedDirectMessageWithPubKeys.forEach(decryptedMessageWithPubKey -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
            if (networkEnvelope instanceof SupportMessage) {
                onSupportMessage((SupportMessage) networkEnvelope);
            } else if (networkEnvelope instanceof AckMessage) {
                onAckMessage((AckMessage) networkEnvelope);
            }
        });
        decryptedDirectMessageWithPubKeys.clear();

        decryptedMailboxMessageWithPubKeys.forEach(decryptedMessageWithPubKey -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
            log.trace("## decryptedMessageWithPubKey message={}", networkEnvelope.getClass().getSimpleName());
            if (networkEnvelope instanceof SupportMessage) {
                SupportMessage supportMessage = (SupportMessage) networkEnvelope;
                onSupportMessage(supportMessage);
                mailboxMessageService.removeMailboxMsg(supportMessage);
            } else if (networkEnvelope instanceof AckMessage) {
                AckMessage ackMessage = (AckMessage) networkEnvelope;
                onAckMessage(ackMessage);
                mailboxMessageService.removeMailboxMsg(ackMessage);
            }
        });
        decryptedMailboxMessageWithPubKeys.clear();
    }
}

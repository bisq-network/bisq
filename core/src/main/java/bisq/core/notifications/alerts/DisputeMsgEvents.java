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

package bisq.core.notifications.alerts;

import bisq.core.locale.Res;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.support.messages.ChatMessage;

import bisq.network.p2p.P2PService;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DisputeMsgEvents {
    private final RefundManager refundManager;
    private final MediationManager mediationManager;
    private final P2PService p2PService;
    private final MobileNotificationService mobileNotificationService;

    @Inject
    public DisputeMsgEvents(RefundManager refundManager,
                            MediationManager mediationManager,
                            P2PService p2PService,
                            MobileNotificationService mobileNotificationService) {
        this.refundManager = refundManager;
        this.mediationManager = mediationManager;
        this.p2PService = p2PService;
        this.mobileNotificationService = mobileNotificationService;
    }

    public void onAllServicesInitialized() {
        refundManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) c -> {
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(this::setDisputeListener);
            }
        });
        refundManager.getDisputesAsObservableList().forEach(this::setDisputeListener);

        mediationManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) c -> {
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(this::setDisputeListener);
            }
        });
        mediationManager.getDisputesAsObservableList().forEach(this::setDisputeListener);

        // We do not need a handling for unread messages as mailbox messages arrive later and will trigger the
        // event listeners. But the existing messages are not causing a notification.
    }

    public static MobileMessage getTestMsg() {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return new MobileMessage(Res.get("account.notifications.dispute.message.title"),
                Res.get("account.notifications.dispute.message.msg", shortId),
                shortId,
                MobileMessageType.DISPUTE);
    }

    private void setDisputeListener(Dispute dispute) {
        log.debug("We got a dispute added. id={}, tradeId={}", dispute.getId(), dispute.getTradeId());
        dispute.getChatMessages().addListener((ListChangeListener<ChatMessage>) c -> {
            log.debug("We got a ChatMessage added. id={}, tradeId={}", dispute.getId(), dispute.getTradeId());
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(chatMessage -> onChatMessage(chatMessage, dispute));
            }
        });
    }

    private void onChatMessage(ChatMessage chatMessage, Dispute dispute) {
        if (chatMessage.getSenderNodeAddress().equals(p2PService.getAddress())) {
            return;
        }

        // We only send msg in case we are not the sender
        String shortId = chatMessage.getShortId();
        MobileMessage message = new MobileMessage(Res.get("account.notifications.dispute.message.title"),
                Res.get("account.notifications.dispute.message.msg", shortId),
                shortId,
                MobileMessageType.DISPUTE);
        try {
            mobileNotificationService.sendMessage(message);
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
        }

        // We check at every new message if it might be a message sent after the dispute had been closed. If that is the
        // case we revert the isClosed flag so that the UI can reopen the dispute and indicate that a new dispute
        // message arrived.
        Optional<ChatMessage> newestChatMessage = dispute.getChatMessages().stream().
                sorted(Comparator.comparingLong(ChatMessage::getDate).reversed()).findFirst();
        // If last message is not a result message we re-open as we might have received a new message from the
        // trader/mediator/arbitrator who has reopened the case
        if (dispute.isClosed() && newestChatMessage.isPresent() && !newestChatMessage.get().isResultMessage(dispute)) {
            log.info("Reopening dispute {} due to new chat message received {}", dispute.getTradeId(), newestChatMessage.get().getUid());
            dispute.reOpen();
            if (dispute.getSupportType() == SupportType.MEDIATION) {
                mediationManager.requestPersistence();
            } else if (dispute.getSupportType() == SupportType.REFUND) {
                refundManager.requestPersistence();
            }
        }
    }
}

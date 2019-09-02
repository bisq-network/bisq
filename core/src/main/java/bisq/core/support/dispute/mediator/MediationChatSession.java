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

package bisq.core.support.dispute.mediator;

import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeChatSession;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportChatMessage;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class MediationChatSession extends DisputeChatSession {

    public MediationChatSession(@Nullable Dispute dispute,
                                DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager) {
        super(dispute, disputeManager, ChatMessage.Type.MEDIATION);
    }

    MediationChatSession(DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager) {
        super(disputeManager, ChatMessage.Type.MEDIATION);

    }

    @Override
    public void dispatchMessage(SupportChatMessage message) {
        log.info("Received {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), message.getTradeId(), message.getUid());

        if (message instanceof OpenNewDisputeMessage) {
            disputeManager.onOpenNewDisputeMessage((OpenNewDisputeMessage) message);
        } else if (message instanceof PeerOpenedDisputeMessage) {
            disputeManager.onPeerOpenedDisputeMessage((PeerOpenedDisputeMessage) message);
        } else if (message instanceof ChatMessage) {
            if (((ChatMessage) message).getType() != ChatMessage.Type.MEDIATION) {
                log.debug("Ignore non dispute type communication message");
                return;
            }
            disputeManager.getChatManager().onDisputeDirectMessage((ChatMessage) message);
        } else if (message instanceof DisputeResultMessage) {
            disputeManager.onDisputeResultMessage((DisputeResultMessage) message);
        } else {
            log.warn("Unsupported message at dispatchMessage.\nmessage=" + message);
        }
    }
}

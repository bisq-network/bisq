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

package bisq.core.dispute.arbitration;

import bisq.core.dispute.Dispute;
import bisq.core.dispute.DisputeChatSession;
import bisq.core.dispute.DisputeList;
import bisq.core.dispute.DisputeManager;
import bisq.core.dispute.arbitration.messages.PeerPublishedDisputePayoutTxMessage;
import bisq.core.dispute.messages.DisputeCommunicationMessage;
import bisq.core.dispute.messages.DisputeMessage;
import bisq.core.dispute.messages.DisputeResultMessage;
import bisq.core.dispute.messages.OpenNewDisputeMessage;
import bisq.core.dispute.messages.PeerOpenedDisputeMessage;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class ArbitrationChatSession extends DisputeChatSession {

    public ArbitrationChatSession(@Nullable Dispute dispute,
                                  DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager) {
        super(dispute, disputeManager, DisputeCommunicationMessage.Type.ARBITRATION);
    }

    ArbitrationChatSession(DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager) {
        super(disputeManager, DisputeCommunicationMessage.Type.ARBITRATION);

    }

    @Override
    public void dispatchMessage(DisputeMessage message) {
        log.info("Received {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), message.getTradeId(), message.getUid());

        if (message instanceof OpenNewDisputeMessage) {
            disputeManager.onOpenNewDisputeMessage((OpenNewDisputeMessage) message);
        } else if (message instanceof PeerOpenedDisputeMessage) {
            disputeManager.onPeerOpenedDisputeMessage((PeerOpenedDisputeMessage) message);
        } else if (message instanceof DisputeCommunicationMessage) {
            if (((DisputeCommunicationMessage) message).getType() != DisputeCommunicationMessage.Type.ARBITRATION) {
                log.debug("Ignore non dispute type communication message");
                return;
            }
            disputeManager.getChatManager().onDisputeDirectMessage((DisputeCommunicationMessage) message);
        } else if (message instanceof DisputeResultMessage) {
            disputeManager.onDisputeResultMessage((DisputeResultMessage) message);
        } else if (message instanceof PeerPublishedDisputePayoutTxMessage) {
            //todo make generic
            ((ArbitrationDisputeManager) disputeManager).onDisputedPayoutTxMessage((PeerPublishedDisputePayoutTxMessage) message);
        } else {
            log.warn("Unsupported message at dispatchMessage.\nmessage=" + message);
        }
    }
}

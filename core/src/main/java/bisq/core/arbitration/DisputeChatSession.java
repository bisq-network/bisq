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

package bisq.core.arbitration;

import bisq.core.chat.ChatManager;
import bisq.core.chat.ChatSession;

import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.arbitration.messages.DisputeCommunicationMessage;
import bisq.core.arbitration.messages.DisputeMessage;
import bisq.core.arbitration.messages.DisputeResultMessage;
import bisq.core.arbitration.messages.OpenNewDisputeMessage;
import bisq.core.arbitration.messages.PeerOpenedDisputeMessage;
import bisq.core.arbitration.messages.PeerPublishedDisputePayoutTxMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class DisputeChatSession extends ChatSession {
    private static final Logger log = LoggerFactory.getLogger(DisputeManager.class);

    private Dispute dispute;
    private DisputeManager disputeManager;
    private ChatManager chatManager;

    public DisputeChatSession(
            @Nullable Dispute dispute,
            DisputeManager disputeManager,
            ChatManager chatManager
    ) {
        this.dispute = dispute;
        this.disputeManager = disputeManager;
        this.chatManager = chatManager;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dependent on selected dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isTrader() {
        return disputeManager.isTrader(dispute);
    }

    @Override
    public String getTradeId() {
        return dispute.getTradeId();
    }

    @Override
    public PubKeyRing getPubKeyRing() {
        return dispute.getTraderPubKeyRing();
    }

    @Override
    public void addDisputeCommunicationMessage(DisputeCommunicationMessage message) {
        if (isTrader() || (!isTrader() && !message.isSystemMessage()))
            dispute.addDisputeCommunicationMessage(message);
    }

    @Override
    public void persist() {
        disputeManager.getDisputes().persist();
    }

    @Override
    public ObservableList<DisputeCommunicationMessage> getDisputeCommunicationMessages() {
        return dispute.getDisputeCommunicationMessages();
    }

    @Override
    public boolean chatIsOpen() {
        return !dispute.isClosed();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Not dependent on selected dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public NodeAddress getPeerNodeAddress(DisputeCommunicationMessage message) {
        Optional<Dispute> disputeOptional = disputeManager.findDispute(message.getTradeId(), message.getTraderId());
        if (!disputeOptional.isPresent()) {
            log.warn("Could not find dispute for tradeId = {} traderId = {}",
                    message.getTradeId(), message.getTraderId());
            return null;
        }
        return disputeManager.getNodeAddressPubKeyRingTuple(disputeOptional.get()).first;
    }

    @Override
    public PubKeyRing getPeerPubKeyRing(DisputeCommunicationMessage message) {
        Optional<Dispute> disputeOptional = disputeManager.findDispute(message.getTradeId(), message.getTraderId());
        if (!disputeOptional.isPresent()) {
            log.warn("Could not find dispute for tradeId = {} traderId = {}",
                    message.getTradeId(), message.getTraderId());
            return null;
        }

        return disputeManager.getNodeAddressPubKeyRingTuple(disputeOptional.get()).second;
    }

    @Override
    public void dispatchMessage(DisputeMessage message) {
        log.info("Received {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), message.getTradeId(), message.getUid());

        if (message instanceof OpenNewDisputeMessage)
            disputeManager.onOpenNewDisputeMessage((OpenNewDisputeMessage) message);
        else if (message instanceof PeerOpenedDisputeMessage)
            disputeManager.onPeerOpenedDisputeMessage((PeerOpenedDisputeMessage) message);
        else if (message instanceof DisputeCommunicationMessage)
            chatManager.onDisputeDirectMessage((DisputeCommunicationMessage) message);
        else if (message instanceof DisputeResultMessage)
            disputeManager.onDisputeResultMessage((DisputeResultMessage) message);
        else if (message instanceof PeerPublishedDisputePayoutTxMessage)
            disputeManager.onDisputedPayoutTxMessage((PeerPublishedDisputePayoutTxMessage) message);
        else
            log.warn("Unsupported message at dispatchMessage.\nmessage=" + message);
    }

    @Override
    public List<DisputeCommunicationMessage> getChatMessages() {
        return disputeManager.getDisputes().getList().stream()
                .flatMap(dispute -> dispute.getDisputeCommunicationMessages().stream())
                .collect(Collectors.toList());
    }

    public boolean channelOpen(DisputeCommunicationMessage message) {
        return disputeManager.findDispute(message.getTradeId(), message.getTraderId()).isPresent();
    }

    public void storeDisputeCommunicationMessage(DisputeCommunicationMessage message) {
        Optional<Dispute> disputeOptional = disputeManager.findDispute(message.getTradeId(), message.getTraderId());

        if (disputeOptional.isPresent()) {
            if (!disputeOptional.get().getDisputeCommunicationMessages().stream()
                    .anyMatch(m -> m.getUid().equals(message.getUid())))
                disputeOptional.get().addDisputeCommunicationMessage(message);
            else
                log.warn("We got a disputeCommunicationMessage what we have already stored. UId = {} TradeId = {}",
                        message.getUid(), message.getTradeId());
        }
    }
}

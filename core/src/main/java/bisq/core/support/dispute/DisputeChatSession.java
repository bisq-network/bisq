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

package bisq.core.support.dispute;

import bisq.core.support.ChatSession;
import bisq.core.support.SupportType;
import bisq.core.support.messages.ChatMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class DisputeChatSession extends ChatSession {
    @Nullable
    @Getter
    private Dispute dispute;
    protected DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager;

    public DisputeChatSession(@Nullable Dispute dispute,
                              DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager,
                              SupportType supportType) {
        super(supportType);
        this.dispute = dispute;
        this.disputeManager = disputeManager;
    }

    public DisputeChatSession(DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager,
                              SupportType supportType) {
        super(supportType);
        this.disputeManager = disputeManager;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dependent on selected dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isMediationDispute() {
        return dispute == null || dispute.isMediationDispute();
    }

    @Override
    public boolean isClient() {
        return dispute != null && disputeManager.isTrader(dispute);
    }

    @Override
    public String getTradeId() {
        return dispute != null ? dispute.getTradeId() : "";
    }

    @Override
    public PubKeyRing getClientPubKeyRing() {
        // Get pubKeyRing of trader. Arbitrator is considered server for the chat session
        return dispute != null ? dispute.getTraderPubKeyRing() : null;
    }

    @Override
    public void addChatMessage(ChatMessage message) {
        if (dispute != null && (isClient() || (!isClient() && !message.isSystemMessage())))
            dispute.addChatMessage(message);
    }

    @Override
    public void persist() {
        DisputeList disputes = disputeManager.getDisputeList();
        if (disputes != null) {
            disputes.persist();
        }
    }

    @Override
    public ObservableList<ChatMessage> getObservableChatMessageList() {
        return dispute != null ? dispute.getChatMessages() : FXCollections.observableArrayList();
    }

    @Override
    public boolean chatIsOpen() {
        return dispute != null && !dispute.isClosed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Not dependent on selected dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    @Override
    public NodeAddress getPeerNodeAddress(ChatMessage message) {
        Optional<Dispute> disputeOptional = disputeManager.findDispute(message);
        if (!disputeOptional.isPresent()) {
            log.warn("Could not find dispute for tradeId = {} traderId = {}",
                    message.getTradeId(), message.getTraderId());
            return null;
        }
        return disputeManager.getNodeAddressPubKeyRingTuple(disputeOptional.get()).first;
    }

    @Nullable
    @Override
    public PubKeyRing getPeerPubKeyRing(ChatMessage message) {
        Optional<Dispute> disputeOptional = disputeManager.findDispute(message);
        if (!disputeOptional.isPresent()) {
            log.warn("Could not find dispute for tradeId = {} traderId = {}",
                    message.getTradeId(), message.getTraderId());
            return null;
        }

        return disputeManager.getNodeAddressPubKeyRingTuple(disputeOptional.get()).second;
    }

    @Override
    public List<ChatMessage> getChatMessages() {
        DisputeList<? extends DisputeList> disputes = disputeManager.getDisputeList();
        if (disputes != null) {
            return disputes.getList().stream()
                    .flatMap(dispute -> dispute.getChatMessages().stream())
                    .collect(Collectors.toList());
        } else {
            log.error("disputes is null");
            return new ArrayList<>();
        }
    }

    @Override
    public boolean channelOpen(ChatMessage message) {
        return disputeManager.findDispute(message).isPresent();
    }

    @Override
    public void storeChatMessage(ChatMessage message) {
        Optional<Dispute> disputeOptional = disputeManager.findDispute(message);
        if (disputeOptional.isPresent()) {
            if (disputeOptional.get().getChatMessages().stream().noneMatch(m -> m.getUid().equals(message.getUid()))) {
                disputeOptional.get().addChatMessage(message);
            } else {
                log.warn("We got a chatMessage what we have already stored. UId = {} TradeId = {}",
                        message.getUid(), message.getTradeId());
            }
        }
    }
}

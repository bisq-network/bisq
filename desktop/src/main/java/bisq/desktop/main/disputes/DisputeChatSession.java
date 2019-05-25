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

package bisq.desktop.main.disputes;

import bisq.desktop.main.Chat.ChatSession;

import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.arbitration.messages.DisputeCommunicationMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import javafx.collections.ObservableList;

public class DisputeChatSession implements ChatSession {
    private Dispute dispute;
    private DisputeManager disputeManager;

    public DisputeChatSession(Dispute dispute, DisputeManager disputeManager) {
        this.dispute = dispute;
        this.disputeManager = disputeManager;
    }

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

    @Override
    public NodeAddress getPeerNodeAddress() {
        return disputeManager.getNodeAddressPubKeyRingTuple(dispute).first;
    }

    @Override
    public PubKeyRing getPeerPubKeyRing() {
        return disputeManager.getNodeAddressPubKeyRingTuple(dispute).second;
    }

}

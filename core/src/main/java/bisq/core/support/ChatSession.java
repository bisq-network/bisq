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

import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.TradeChatMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import javafx.collections.ObservableList;

import java.util.List;

import lombok.Getter;

public abstract class ChatSession {
    @Getter
    ChatMessage.Type type;

    public ChatSession(ChatMessage.Type type) {
        this.type = type;
    }

    abstract public boolean isClient();

    //todo remove
    abstract public boolean isMediationDispute();

    abstract public String getTradeId();

    abstract public PubKeyRing getClientPubKeyRing();

    abstract public void addChatMessage(ChatMessage message);

    abstract public void persist();

    abstract public ObservableList<ChatMessage> getDisputeCommunicationMessages();

    abstract public List<ChatMessage> getChatMessages();

    abstract public boolean chatIsOpen();

    abstract public NodeAddress getPeerNodeAddress(ChatMessage message);

    abstract public PubKeyRing getPeerPubKeyRing(ChatMessage message);

    abstract public void dispatchMessage(TradeChatMessage message);

    abstract public boolean channelOpen(ChatMessage message);

    abstract public void storeDisputeCommunicationMessage(ChatMessage message);
}

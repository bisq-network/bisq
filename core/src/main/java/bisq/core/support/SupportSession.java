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

import bisq.common.crypto.PubKeyRing;

import javafx.collections.ObservableList;

import lombok.Getter;

public abstract class SupportSession {

    // todo refactor ui so that can be converted to isTrader
    @Getter
    private boolean isClient;


    protected SupportSession(boolean isClient) {
        this.isClient = isClient;
    }

    protected SupportSession() {
    }

    abstract public String getTradeId();

    abstract public PubKeyRing getClientPubKeyRing();

    abstract public void addChatMessage(ChatMessage message);

    abstract public ObservableList<ChatMessage> getObservableChatMessageList();

    abstract public boolean chatIsOpen();

    public abstract boolean isDisputeAgent();
}

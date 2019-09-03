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

package bisq.core.support.traderchat;

import bisq.core.support.SupportSession;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.Trade;

import bisq.common.crypto.PubKeyRing;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/* Makers are considered as servers and takers as clients for trader to trader chat
 * sessions. This is only to make it easier to understand who's who, there is no real
 * server/client relationship */
@Slf4j
public class TradeChatSession extends SupportSession {

    @Nullable
    private Trade trade;

    public TradeChatSession(@Nullable Trade trade,
                            boolean isClient) {
        super(isClient);
        this.trade = trade;
    }


    @Override
    public String getTradeId() {
        return trade != null ? trade.getId() : "";
    }

    @Override
    public PubKeyRing getClientPubKeyRing() {
        // Get pubkeyring of taker. Maker is considered server for chat sessions
        if (trade != null && trade.getContract() != null)
            return trade.getContract().getTakerPubKeyRing();
        return null;
    }

    @Override
    public void addChatMessage(ChatMessage message) {
        if (trade != null)
            trade.addCommunicationMessage(message);
    }

    @Override
    public ObservableList<ChatMessage> getObservableChatMessageList() {
        return trade != null ? trade.getCommunicationMessages() : FXCollections.observableArrayList();
    }

    @Override
    public boolean chatIsOpen() {
        return trade != null && trade.getState() != Trade.State.WITHDRAW_COMPLETED;
    }

    @Override
    public boolean isDisputeAgent() {
        return false;
    }
}

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
import bisq.core.trade.model.bisq_v1.Trade;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

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
    public int getClientId() {
        // TODO remove that client-server concept for trade chat
        // Get pubKeyRing of taker. Maker is considered server for chat sessions
        try {
            return trade.getContract().getTakerPubKeyRing().hashCode();
        } catch (NullPointerException e) {
            log.warn("Unable to get takerPubKeyRing from Trade Contract - {}", e.toString());
        }
        return 0;
    }

    @Override
    public ObservableList<ChatMessage> getObservableChatMessageList() {
        return trade != null ? trade.getChatMessages() : FXCollections.observableArrayList();
    }

    @Override
    public boolean chatIsOpen() {
        return trade != null && trade.getTradeState() != Trade.State.WITHDRAW_COMPLETED;
    }

    @Override
    public boolean isDisputeAgent() {
        return false;
    }
}

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

import bisq.core.support.SupportSession;
import bisq.core.support.messages.ChatMessage;

import bisq.common.crypto.PubKeyRing;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class DisputeSession extends SupportSession {
    @Nullable
    private Dispute dispute;
    private final boolean isTrader;

    public DisputeSession(@Nullable Dispute dispute, boolean isTrader) {
        super();
        this.dispute = dispute;
        this.isTrader = isTrader;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dependent on selected dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isClient() {
        return isTrader;
    }

    @Override
    public String getTradeId() {
        return dispute != null ? dispute.getTradeId() : "";
    }

    @Override
    public int getClientId() {
        // Get pubKeyRing of trader. Arbitrator is considered server for the chat session
        try {
            return dispute.getTraderPubKeyRing().hashCode();
        } catch (NullPointerException e) {
            log.warn("Unable to get traderPubKeyRing from Dispute - {}", e.toString());
        }
        return 0;
    }

    @Override
    public ObservableList<ChatMessage> getObservableChatMessageList() {
        return dispute != null ? dispute.getChatMessages() : FXCollections.observableArrayList();
    }

    @Override
    public boolean chatIsOpen() {
        return dispute != null && !dispute.isClosed();
    }

    @Override
    public boolean isDisputeAgent() {
        return !isClient();
    }
}

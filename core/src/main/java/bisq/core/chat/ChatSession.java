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

package bisq.core.chat;

import bisq.core.arbitration.messages.DisputeCommunicationMessage;
import bisq.core.arbitration.messages.DisputeMessage;

import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import javafx.scene.control.Button;

import javafx.beans.property.ReadOnlyDoubleProperty;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

import lombok.Getter;

public abstract class ChatSession {
    abstract public boolean isTrader();

    abstract public String getTradeId();

    abstract public PubKeyRing getPubKeyRing();

    abstract public void addDisputeCommunicationMessage(DisputeCommunicationMessage message);

    abstract public void persist();

    abstract public ObservableList<DisputeCommunicationMessage> getDisputeCommunicationMessages();

    abstract public List<DisputeCommunicationMessage> getChatMessages();

    abstract public boolean chatIsOpen();

    abstract public NodeAddress getPeerNodeAddress(DisputeCommunicationMessage message);

    abstract public PubKeyRing getPeerPubKeyRing(DisputeCommunicationMessage message);

    abstract public void dispatchMessage(DisputeMessage message);

    abstract public boolean channelOpen(DisputeCommunicationMessage message);

    abstract public void storeDisputeCommunicationMessage(DisputeCommunicationMessage message);
}

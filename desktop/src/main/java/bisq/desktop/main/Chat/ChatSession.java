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

package bisq.desktop.main.Chat;

import bisq.core.arbitration.messages.DisputeCommunicationMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import javafx.scene.control.Button;

import javafx.beans.property.ReadOnlyDoubleProperty;

import javafx.collections.ObservableList;

public interface ChatSession {
    boolean isTrader();

    String getTradeId();

    PubKeyRing getPubKeyRing();

    void addDisputeCommunicationMessage(DisputeCommunicationMessage message);

    void persist();

    ObservableList<DisputeCommunicationMessage> getDisputeCommunicationMessages();

    boolean chatIsOpen();

    Button extraButton();

    NodeAddress getPeerNodeAddress();

    PubKeyRing getPeerPubKeyRing();

    ReadOnlyDoubleProperty widthProperty();
}

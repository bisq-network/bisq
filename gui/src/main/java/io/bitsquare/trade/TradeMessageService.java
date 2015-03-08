/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade;

import io.bitsquare.network.Message;
import io.bitsquare.network.MessageBroker;
import io.bitsquare.trade.listeners.GetPeerAddressListener;
import io.bitsquare.trade.listeners.IncomingMessageListener;
import io.bitsquare.trade.listeners.OutgoingMessageListener;
import io.bitsquare.network.Peer;

import java.security.PublicKey;

public interface TradeMessageService extends MessageBroker {

    void sendMessage(Peer peer, Message message, OutgoingMessageListener listener);

    void addIncomingMessageListener(IncomingMessageListener listener);

    void removeIncomingMessageListener(IncomingMessageListener listener);

    void getPeerAddress(PublicKey messagePublicKey, GetPeerAddressListener getPeerAddressListener);
}

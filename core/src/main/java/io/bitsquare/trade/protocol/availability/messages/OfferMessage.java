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

package io.bitsquare.trade.protocol.availability.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.messaging.DirectMessage;

import javax.annotation.concurrent.Immutable;

@Immutable
public abstract class OfferMessage implements DirectMessage {
    //TODO add serialVersionUID also in superclasses as changes would break compatibility
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();

    public final String offerId;

    OfferMessage(String offerId) {
        this.offerId = offerId;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "OfferMessage{" +
                "messageVersion=" + messageVersion +
                ", offerId='" + offerId + '\'' +
                '}';
    }
}

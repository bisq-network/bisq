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

package io.bitsquare.trade.protocol.trade.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.messaging.DirectMessage;

import javax.annotation.concurrent.Immutable;

@Immutable
public abstract class TradeMessage implements DirectMessage {
    //TODO add serialVersionUID also in superclasses as changes would break compatibility
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
    public final String tradeId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeMessage)) return false;

        TradeMessage that = (TradeMessage) o;

        return !(tradeId != null ? !tradeId.equals(that.tradeId) : that.tradeId != null);

    }

    @Override
    public int hashCode() {
        return tradeId != null ? tradeId.hashCode() : 0;
    }

    protected TradeMessage(String tradeId) {
        this.tradeId = tradeId;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }
}

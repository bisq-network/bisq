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
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.messaging.MailboxMessage;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;

@Immutable
public class DepositTxPublishedMessage extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final byte[] depositTx;
    private final Address senderAddress;

    public DepositTxPublishedMessage(String tradeId, byte[] depositTx, Address senderAddress) {
        super(tradeId);
        this.depositTx = depositTx;
        this.senderAddress = senderAddress;
    }

    @Override
    public Address getSenderAddress() {
        return senderAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DepositTxPublishedMessage)) return false;
        if (!super.equals(o)) return false;

        DepositTxPublishedMessage that = (DepositTxPublishedMessage) o;

        if (!Arrays.equals(depositTx, that.depositTx)) return false;
        return !(senderAddress != null ? !senderAddress.equals(that.senderAddress) : that.senderAddress != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (depositTx != null ? Arrays.hashCode(depositTx) : 0);
        result = 31 * result + (senderAddress != null ? senderAddress.hashCode() : 0);
        return result;
    }
}

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
public class FinalizePayoutTxRequest extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final byte[] sellerSignature;
    public final String sellerPayoutAddress;
    public final long lockTime;
    private final Address senderAddress;

    public FinalizePayoutTxRequest(String tradeId,
                                   byte[] sellerSignature,
                                   String sellerPayoutAddress,
                                   long lockTime,
                                   Address senderAddress) {
        super(tradeId);
        this.sellerSignature = sellerSignature;
        this.sellerPayoutAddress = sellerPayoutAddress;
        this.lockTime = lockTime;
        this.senderAddress = senderAddress;
    }

    @Override
    public Address getSenderAddress() {
        return senderAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinalizePayoutTxRequest)) return false;
        if (!super.equals(o)) return false;

        FinalizePayoutTxRequest that = (FinalizePayoutTxRequest) o;

        if (lockTime != that.lockTime) return false;
        if (!Arrays.equals(sellerSignature, that.sellerSignature)) return false;
        if (sellerPayoutAddress != null ? !sellerPayoutAddress.equals(that.sellerPayoutAddress) : that.sellerPayoutAddress != null)
            return false;
        return !(senderAddress != null ? !senderAddress.equals(that.senderAddress) : that.senderAddress != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (sellerSignature != null ? Arrays.hashCode(sellerSignature) : 0);
        result = 31 * result + (sellerPayoutAddress != null ? sellerPayoutAddress.hashCode() : 0);
        result = 31 * result + (int) (lockTime ^ (lockTime >>> 32));
        result = 31 * result + (senderAddress != null ? senderAddress.hashCode() : 0);
        return result;
    }
}

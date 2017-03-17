/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.wire.message.trade;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.wire.proto.Messages;
import io.bisq.wire.message.ToProtoBuffer;
import io.bisq.wire.message.p2p.MailboxMessage;
import io.bisq.wire.payload.p2p.NodeAddress;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.UUID;

@Immutable
public final class FinalizePayoutTxRequest extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final byte[] sellerSignature;
    public final String sellerPayoutAddress;
    public final long lockTimeAsBlockHeight;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    public FinalizePayoutTxRequest(String tradeId,
                                   byte[] sellerSignature,
                                   String sellerPayoutAddress,
                                   long lockTimeAsBlockHeight,
                                   NodeAddress senderNodeAddress) {
        this(tradeId, sellerSignature, sellerPayoutAddress, lockTimeAsBlockHeight, senderNodeAddress,
                UUID.randomUUID().toString());
    }

    public FinalizePayoutTxRequest(String tradeId,
                                   byte[] sellerSignature,
                                   String sellerPayoutAddress,
                                   long lockTimeAsBlockHeight,
                                   NodeAddress senderNodeAddress,
                                   String uid) {
        super(tradeId);
        this.sellerSignature = sellerSignature;
        this.sellerPayoutAddress = sellerPayoutAddress;
        this.lockTimeAsBlockHeight = lockTimeAsBlockHeight;
        this.senderNodeAddress = senderNodeAddress;
        this.uid = uid;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinalizePayoutTxRequest)) return false;
        if (!super.equals(o)) return false;

        FinalizePayoutTxRequest that = (FinalizePayoutTxRequest) o;

        if (lockTimeAsBlockHeight != that.lockTimeAsBlockHeight) return false;
        if (!Arrays.equals(sellerSignature, that.sellerSignature)) return false;
        if (sellerPayoutAddress != null ? !sellerPayoutAddress.equals(that.sellerPayoutAddress) : that.sellerPayoutAddress != null)
            return false;
        if (senderNodeAddress != null ? !senderNodeAddress.equals(that.senderNodeAddress) : that.senderNodeAddress != null)
            return false;
        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (sellerSignature != null ? Arrays.hashCode(sellerSignature) : 0);
        result = 31 * result + (sellerPayoutAddress != null ? sellerPayoutAddress.hashCode() : 0);
        result = 31 * result + (int) (lockTimeAsBlockHeight ^ (lockTimeAsBlockHeight >>> 32));
        result = 31 * result + (senderNodeAddress != null ? senderNodeAddress.hashCode() : 0);
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        return result;
    }

    @Override
    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder baseEnvelope = ToProtoBuffer.getBaseEnvelope();
        return baseEnvelope.setFinalizePayoutTxRequest(Messages.FinalizePayoutTxRequest.newBuilder()
                .setMessageVersion(getMessageVersion())
                .setTradeId(tradeId)
                .setSellerSignature(ByteString.copyFrom(sellerSignature))
                .setSellerPayoutAddress(sellerPayoutAddress)
                .setLockTimeAsBlockHeight(lockTimeAsBlockHeight)
                .setSenderNodeAddress(senderNodeAddress.toProtoBuf())
                .setUid(uid)).build();
    }
}

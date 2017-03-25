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

package io.bisq.protobuffer.message.trade;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import lombok.EqualsAndHashCode;

import javax.annotation.concurrent.Immutable;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Immutable
public final class FiatTransferStartedMessage extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final String buyerPayoutAddress;
    private final NodeAddress senderNodeAddress;
    private final String uid;
    public final byte[] buyerSignature;

    public FiatTransferStartedMessage(String tradeId, String buyerPayoutAddress,
                                      NodeAddress senderNodeAddress,
                                      byte[] buyerSignature,
                                      String uid) {
        super(tradeId);
        this.buyerPayoutAddress = buyerPayoutAddress;
        this.senderNodeAddress = senderNodeAddress;
        this.buyerSignature = buyerSignature;
        this.uid = uid;
    }

    public FiatTransferStartedMessage(String tradeId, String buyerPayoutAddress,
                                      NodeAddress senderNodeAddress, byte[] buyerSignature) {
        this(tradeId, buyerPayoutAddress, senderNodeAddress, buyerSignature, UUID.randomUUID().toString());
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
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        return baseEnvelope.setFiatTransferStartedMessage(baseEnvelope.getFiatTransferStartedMessageBuilder()
                .setMessageVersion(getMessageVersion())
                .setTradeId(tradeId)
                .setBuyerSignature(ByteString.copyFrom(buyerSignature))
                .setBuyerPayoutAddress(buyerPayoutAddress)
                .setSenderNodeAddress(senderNodeAddress.toProto())
                .setUid(uid)).build();
    }

    // We dont want to log the buyerSignature!
    @Override
    public String toString() {
        return "FiatTransferStartedMessage{" +
                "buyerPayoutAddress='" + buyerPayoutAddress + '\'' +
                ", senderNodeAddress=" + senderNodeAddress +
                ", uid='" + uid + '\'' +
                "} " + super.toString();
    }
}

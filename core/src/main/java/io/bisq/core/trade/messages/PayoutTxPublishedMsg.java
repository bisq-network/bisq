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

package io.bisq.core.trade.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMsg;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;

import javax.annotation.concurrent.Immutable;

@EqualsAndHashCode(callSuper = true)
@Immutable
public final class PayoutTxPublishedMsg extends TradeMsg implements MailboxMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final byte[] payoutTx;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    public PayoutTxPublishedMsg(String tradeId, byte[] payoutTx, NodeAddress senderNodeAddress, String uid) {
        super(tradeId);
        this.payoutTx = payoutTx;
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
    public PB.WireEnvelope toProtoMsg() {
        PB.WireEnvelope.Builder msgBuilder = NetworkEnvelope.getMsgBuilder();
        return msgBuilder.setPayoutTxPublishedMessage(msgBuilder.getPayoutTxPublishedMessageBuilder()
                .setUid(uid)
                .setMessageVersion(getMsgVersion())
                .setTradeId(tradeId)
                .setPayoutTx(ByteString.copyFrom(payoutTx))
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())).build();
    }

    // payoutTx not printed for privacy reasons
    @Override
    public String toString() {
        return "PayoutTxPublishedMessage{" +
                "payoutTx not printed for privacy reasons..." +
                ", senderNodeAddress=" + senderNodeAddress +
                ", uid='" + uid + '\'' +
                "} " + super.toString();
    }
}

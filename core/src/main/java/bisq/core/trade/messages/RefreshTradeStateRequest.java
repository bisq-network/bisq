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

package bisq.core.trade.messages;

import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class RefreshTradeStateRequest extends TradeMessage implements MailboxMessage {
    private final NodeAddress senderNodeAddress;

    public RefreshTradeStateRequest(String uid,
                                    String tradeId,
                                    NodeAddress senderNodeAddress) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RefreshTradeStateRequest(int messageVersion,
                                     String uid,
                                     String tradeId,
                                     NodeAddress senderNodeAddress) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        final protobuf.RefreshTradeStateRequest.Builder builder = protobuf.RefreshTradeStateRequest.newBuilder();
        builder.setUid(uid)
                .setTradeId(tradeId)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage());
        return getNetworkEnvelopeBuilder().setRefreshTradeStateRequest(builder).build();
    }

    public static RefreshTradeStateRequest fromProto(protobuf.RefreshTradeStateRequest proto, int messageVersion) {
        return new RefreshTradeStateRequest(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()));
    }

    @Override
    public String toString() {
        return "RefreshTradeStateRequest{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                "\n} " + super.toString();
    }

}

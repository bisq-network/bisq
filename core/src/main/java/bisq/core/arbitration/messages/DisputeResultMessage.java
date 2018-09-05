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

package bisq.core.arbitration.messages;

import bisq.core.arbitration.DisputeResult;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value
@EqualsAndHashCode(callSuper = true)
public final class DisputeResultMessage extends DisputeMessage {
    private final DisputeResult disputeResult;
    private final NodeAddress senderNodeAddress;

    public DisputeResultMessage(DisputeResult disputeResult,
                                NodeAddress senderNodeAddress,
                                String uid) {
        this(disputeResult,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DisputeResultMessage(DisputeResult disputeResult,
                                 NodeAddress senderNodeAddress,
                                 String uid,
                                 int messageVersion) {
        super(messageVersion, uid);
        this.disputeResult = disputeResult;
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setDisputeResultMessage(PB.DisputeResultMessage.newBuilder()
                        .setDisputeResult(disputeResult.toProtoMessage())
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setUid(uid))
                .build();
    }

    public static DisputeResultMessage fromProto(PB.DisputeResultMessage proto, int messageVersion) {
        checkArgument(proto.hasDisputeResult(), "DisputeResult must be set");
        return new DisputeResultMessage(DisputeResult.fromProto(proto.getDisputeResult()),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion);
    }

    @Override
    public String getTradeId() {
        return disputeResult.getTradeId();
    }

    @Override
    public String toString() {
        return "DisputeResultMessage{" +
                "\n     disputeResult=" + disputeResult +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     DisputeResultMessage.uid='" + uid + '\'' +
                ",\n     messageVersion=" + messageVersion +
                "\n} " + super.toString();
    }
}

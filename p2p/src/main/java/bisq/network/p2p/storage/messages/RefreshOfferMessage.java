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

package bisq.network.p2p.storage.messages;

import bisq.common.app.Version;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class RefreshOfferMessage extends BroadcastMessage {
    private final byte[] hashOfDataAndSeqNr;     // 32 bytes
    private final byte[] signature;              // 46 bytes
    private final byte[] hashOfPayload;          // 32 bytes
    private final int sequenceNumber;            // 4 bytes

    public RefreshOfferMessage(byte[] hashOfDataAndSeqNr,
                               byte[] signature,
                               byte[] hashOfPayload,
                               int sequenceNumber) {
        this(hashOfDataAndSeqNr, signature, hashOfPayload, sequenceNumber, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RefreshOfferMessage(byte[] hashOfDataAndSeqNr,
                                byte[] signature,
                                byte[] hashOfPayload,
                                int sequenceNumber,
                                int messageVersion) {
        super(messageVersion);
        this.hashOfDataAndSeqNr = hashOfDataAndSeqNr;
        this.signature = signature;
        this.hashOfPayload = hashOfPayload;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setRefreshOfferMessage(protobuf.RefreshOfferMessage.newBuilder()
                        .setHashOfDataAndSeqNr(ByteString.copyFrom(hashOfDataAndSeqNr))
                        .setSignature(ByteString.copyFrom(signature))
                        .setHashOfPayload(ByteString.copyFrom(hashOfPayload))
                        .setSequenceNumber(sequenceNumber))
                .build();
    }

    public static RefreshOfferMessage fromProto(protobuf.RefreshOfferMessage proto, int messageVersion) {
        return new RefreshOfferMessage(proto.getHashOfDataAndSeqNr().toByteArray(),
                proto.getSignature().toByteArray(),
                proto.getHashOfPayload().toByteArray(),
                proto.getSequenceNumber(),
                messageVersion);
    }
}

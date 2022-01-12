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

package bisq.network.p2p;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class FileTransferPart extends NetworkEnvelope implements ExtendedDataSizePermission, SendersNodeAddressMessage {
    NodeAddress senderNodeAddress;
    public String uid;
    public String tradeId;
    public int traderId;
    public long seqNumOrFileLength;
    public ByteString messageData;   // if message_data is empty it is the first message, requesting file upload permission

    public FileTransferPart(NodeAddress senderNodeAddress,
                            String tradeId,
                            int traderId,
                            String uid,
                            long seqNumOrFileLength,
                            ByteString messageData) {
        this(senderNodeAddress, tradeId, traderId, uid, seqNumOrFileLength, messageData, Version.getP2PMessageVersion());
    }

    public boolean isInitialRequest() {
        return messageData.size() == 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private FileTransferPart(NodeAddress senderNodeAddress,
                             String tradeId,
                             int traderId,
                             String uid,
                             long seqNumOrFileLength,
                             ByteString messageData,
                             int messageVersion) {
        super(messageVersion);
        this.senderNodeAddress = senderNodeAddress;
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.uid = uid;
        this.seqNumOrFileLength = seqNumOrFileLength;
        this.messageData = messageData;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setFileTransferPart(protobuf.FileTransferPart.newBuilder()
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setTradeId(tradeId)
                        .setTraderId(traderId)
                        .setUid(uid)
                        .setSeqNumOrFileLength(seqNumOrFileLength)
                        .setMessageData(messageData)
                        .build())
                .build();
    }

    public static FileTransferPart fromProto(protobuf.FileTransferPart proto, int messageVersion) {
        return new FileTransferPart(
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getTradeId(),
                proto.getTraderId(),
                proto.getUid(),
                proto.getSeqNumOrFileLength(),
                proto.getMessageData(),
                messageVersion);
    }

    @Override
    public String toString() {
        return "FileTransferPart{" +
                "\n     senderNodeAddress='" + senderNodeAddress.getHostNameForDisplay() + '\'' +
                ",\n     uid='" + uid + '\'' +
                ",\n     tradeId='" + tradeId + '\'' +
                ",\n     traderId='" + traderId + '\'' +
                ",\n     seqNumOrFileLength=" + seqNumOrFileLength +
                "\n} " + super.toString();
    }
}

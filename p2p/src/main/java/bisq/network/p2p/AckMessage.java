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

import bisq.network.p2p.mailbox.MailboxMessage;
import bisq.network.p2p.storage.payload.ExpirablePayload;

import bisq.common.app.Version;
import bisq.common.proto.ProtoUtil;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistablePayload;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

// TODO ExpirablePayload has no effect here as it is either a direct msg or packed into MailboxStoragePayload
// We could extend the TTL by setting the TTL in MailboxStoragePayload from the type of msg which gets into the
// SealedAndSigned data.

// We exclude uid from hashcode and equals to detect duplicate entries of the same AckMessage
@EqualsAndHashCode(callSuper = true, exclude = {"uid"})
@Value
@Slf4j
public final class AckMessage extends NetworkEnvelope implements MailboxMessage, PersistablePayload, ExpirablePayload {
    public static final long TTL = TimeUnit.DAYS.toMillis(7);

    private final String uid;
    private final NodeAddress senderNodeAddress;
    private final AckMessageSourceType sourceType;
    private final String sourceMsgClassName;
    @Nullable
    private final String sourceUid;
    private final String sourceId;
    private final boolean success;
    @Nullable
    private final String errorMessage;

    /**
     *
     * @param senderNodeAddress       Address of sender
     * @param sourceType            Type of source e.g. TradeMessage, DisputeMessage,...
     * @param sourceMsgClassName    Class name of source msg
     * @param sourceUid             Optional Uid of source (TradeMessage). Can be null if we receive trades/offers from old clients
     * @param sourceId              Id of source (tradeId, disputeId)
     * @param success               True if source message was processed successfully
     * @param errorMessage          Optional error message if source message processing failed
     */
    public AckMessage(NodeAddress senderNodeAddress,
                      AckMessageSourceType sourceType,
                      String sourceMsgClassName,
                      String sourceUid,
                      String sourceId,
                      boolean success,
                      String errorMessage) {
        this(UUID.randomUUID().toString(),
                senderNodeAddress,
                sourceType,
                sourceMsgClassName,
                sourceUid,
                sourceId,
                success,
                errorMessage,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AckMessage(String uid,
                       NodeAddress senderNodeAddress,
                       AckMessageSourceType sourceType,
                       String sourceMsgClassName,
                       @Nullable String sourceUid,
                       String sourceId,
                       boolean success,
                       @Nullable String errorMessage,
                       int messageVersion) {
        super(messageVersion);
        this.uid = uid;
        this.senderNodeAddress = senderNodeAddress;
        this.sourceType = sourceType;
        this.sourceMsgClassName = sourceMsgClassName;
        this.sourceUid = sourceUid;
        this.sourceId = sourceId;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public protobuf.AckMessage toProtoMessage() {
        return getBuilder().build();
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder().setAckMessage(getBuilder()).build();
    }

    public protobuf.AckMessage.Builder getBuilder() {
        protobuf.AckMessage.Builder builder = protobuf.AckMessage.newBuilder()
                .setUid(uid)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setSourceType(sourceType.name())
                .setSourceMsgClassName(sourceMsgClassName)
                .setSourceId(sourceId)
                .setSuccess(success);
        Optional.ofNullable(sourceUid).ifPresent(builder::setSourceUid);
        Optional.ofNullable(errorMessage).ifPresent(builder::setErrorMessage);
        return builder;
    }

    public static AckMessage fromProto(protobuf.AckMessage proto, int messageVersion) {
        AckMessageSourceType sourceType = ProtoUtil.enumFromProto(AckMessageSourceType.class, proto.getSourceType());
        return new AckMessage(proto.getUid(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                sourceType,
                proto.getSourceMsgClassName(),
                proto.getSourceUid().isEmpty() ? null : proto.getSourceUid(),
                proto.getSourceId(),
                proto.getSuccess(),
                proto.getErrorMessage().isEmpty() ? null : proto.getErrorMessage(),
                messageVersion);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public String toString() {
        return "AckMessage{" +
                "\n     uid='" + uid + '\'' +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     sourceType=" + sourceType +
                ",\n     sourceMsgClassName='" + sourceMsgClassName + '\'' +
                ",\n     sourceUid='" + sourceUid + '\'' +
                ",\n     sourceId='" + sourceId + '\'' +
                ",\n     success=" + success +
                ",\n     errorMessage='" + errorMessage + '\'' +
                "\n} " + super.toString();
    }
}

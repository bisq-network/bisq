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

package bisq.core.support.dispute.refund.refundagent;

import bisq.core.support.dispute.agent.DisputeAgent;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.PubKeyRing;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.proto.ProtoUtil;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Getter
public final class RefundAgent extends DisputeAgent implements CapabilityRequiringPayload {

    public RefundAgent(NodeAddress nodeAddress,
                       PubKeyRing pubKeyRing,
                       List<String> languageCodes,
                       long registrationDate,
                       byte[] registrationPubKey,
                       String registrationSignature,
                       @Nullable String emailAddress,
                       @Nullable String info) {

        super(nodeAddress,
                pubKeyRing,
                languageCodes,
                registrationDate,
                registrationPubKey,
                registrationSignature,
                emailAddress,
                info);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        protobuf.RefundAgent.Builder builder = protobuf.RefundAgent.newBuilder()
                .setNodeAddress(nodeAddress.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .addAllLanguageCodes(languageCodes)
                .setRegistrationDate(registrationDate)
                .setRegistrationPubKey(ByteString.copyFrom(registrationPubKey))
                .setRegistrationSignature(registrationSignature);
        Optional.ofNullable(emailAddress).ifPresent(builder::setEmailAddress);
        Optional.ofNullable(info).ifPresent(builder::setInfo);
        return protobuf.StoragePayload.newBuilder().setRefundAgent(builder).build();
    }

    public static RefundAgent fromProto(protobuf.RefundAgent proto) {
        // ExtraDataMap was always null and is not supported anymore since v1.10.2.
        // It is not expected that any historical data exist with a non-empty ExtraDataMap.
        checkArgument(proto.getExtraDataMap().isEmpty(),
                "ExtraDataMap is expected to be not set in RefundAgent");

        return new RefundAgent(NodeAddress.fromProto(proto.getNodeAddress()),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                new ArrayList<>(proto.getLanguageCodesList()),
                proto.getRegistrationDate(),
                proto.getRegistrationPubKey().toByteArray(),
                proto.getRegistrationSignature(),
                ProtoUtil.stringOrNullFromProto(proto.getEmailAddress()),
                ProtoUtil.stringOrNullFromProto(proto.getInfo()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final CanonicalSchema<RefundAgent> PAYLOAD_SCHEMA =
            DisputeAgent.<RefundAgent>getDisputeAgentSchemaBuilder()
                    .string(7, refundAgent -> refundAgent.emailAddress)
                    .string(8, refundAgent -> refundAgent.info)
                    .build();

    public static final CanonicalSchema<RefundAgent> SCHEMA =
            CanonicalSchema.<RefundAgent>newBuilder()
                    .oneof(9, refundAgent -> refundAgent, PAYLOAD_SCHEMA)
                    .build();

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public String toString() {
        return "RefundAgent{} " + super.toString();
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.REFUND_AGENT);
    }
}

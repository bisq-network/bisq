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

package bisq.network.p2p.peers.getdata.messages;

import bisq.network.p2p.AnonymousMessage;
import bisq.network.p2p.SupportedCapabilitiesMessage;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;
import bisq.common.proto.ProtoUtil;

import protobuf.NetworkEnvelope;

import com.google.protobuf.ByteString;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class PreliminaryGetDataRequest extends GetDataRequest implements AnonymousMessage, SupportedCapabilitiesMessage {
    private final Capabilities supportedCapabilities;

    public PreliminaryGetDataRequest(int nonce, Set<byte[]> excludedKeys) {
        this(nonce,
                excludedKeys,
                Version.VERSION,
                Capabilities.app,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PreliminaryGetDataRequest(int nonce,
                                      Set<byte[]> excludedKeys,
                                      @Nullable String version,
                                      Capabilities supportedCapabilities,
                                      int messageVersion) {
        super(messageVersion, nonce, excludedKeys, version);

        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.PreliminaryGetDataRequest.Builder builder = protobuf.PreliminaryGetDataRequest.newBuilder()
                .addAllSupportedCapabilities(Capabilities.toIntList(supportedCapabilities))
                .setNonce(nonce)
                .addAllExcludedKeys(excludedKeys.stream()
                        .map(ByteString::copyFrom)
                        .collect(Collectors.toList()));
        Optional.ofNullable(version).ifPresent(builder::setVersion);
        NetworkEnvelope proto = getNetworkEnvelopeBuilder()
                .setPreliminaryGetDataRequest(builder)
                .build();
        log.info("Sending a PreliminaryGetDataRequest with {} kB and {} excluded key entries. Requesters version={}",
                proto.getSerializedSize() / 1000d, excludedKeys.size(), version);
        return proto;
    }

    public static PreliminaryGetDataRequest fromProto(protobuf.PreliminaryGetDataRequest proto, int messageVersion) {
        Set<byte[]> excludedKeys = ProtoUtil.byteSetFromProtoByteStringList(proto.getExcludedKeysList());
        String requestersVersion = ProtoUtil.stringOrNullFromProto(proto.getVersion());
        log.info("Received a PreliminaryGetDataRequest with {} kB and {} excluded key entries. Requesters version={}",
                proto.getSerializedSize() / 1000d, excludedKeys.size(), requestersVersion);
        return new PreliminaryGetDataRequest(proto.getNonce(),
                excludedKeys,
                requestersVersion,
                Capabilities.fromIntList(proto.getSupportedCapabilitiesList()),
                messageVersion);
    }
}

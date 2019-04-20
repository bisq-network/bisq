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

import io.bisq.generated.protobuffer.PB;

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
    @Nullable
    private final Capabilities supportedCapabilities;

    public PreliminaryGetDataRequest(int nonce,
                                     Set<byte[]> excludedKeys) {
        this(nonce, excludedKeys, Capabilities.app, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PreliminaryGetDataRequest(int nonce,
                                      Set<byte[]> excludedKeys,
                                      @Nullable Capabilities supportedCapabilities,
                                      int messageVersion) {
        super(messageVersion, nonce, excludedKeys);

        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.PreliminaryGetDataRequest.Builder builder = PB.PreliminaryGetDataRequest.newBuilder()
                .setNonce(nonce)
                .addAllExcludedKeys(excludedKeys.stream()
                        .map(ByteString::copyFrom)
                        .collect(Collectors.toList()));

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(Capabilities.toIntList(supportedCapabilities)));

        return getNetworkEnvelopeBuilder()
                .setPreliminaryGetDataRequest(builder)
                .build();
    }

    public static PreliminaryGetDataRequest fromProto(PB.PreliminaryGetDataRequest proto, int messageVersion) {
        Capabilities supportedCapabilities = proto.getSupportedCapabilitiesList().isEmpty() ?
                null :
                Capabilities.fromIntList(proto.getSupportedCapabilitiesList());

        return new PreliminaryGetDataRequest(proto.getNonce(),
                ProtoUtil.byteSetFromProtoByteStringList(proto.getExcludedKeysList()),
                supportedCapabilities,
                messageVersion);
    }
}

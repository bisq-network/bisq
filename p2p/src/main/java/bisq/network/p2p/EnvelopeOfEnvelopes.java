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
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class EnvelopeOfEnvelopes extends NetworkEnvelope implements ExtendedDataSizePermission {

    private final List<NetworkEnvelope> envelopes;

    public EnvelopeOfEnvelopes() {
        this(new ArrayList<>(), Version.getP2PMessageVersion());
    }

    public void add(NetworkEnvelope networkEnvelope) {
        envelopes.add(networkEnvelope);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private EnvelopeOfEnvelopes(List<NetworkEnvelope> envelopes, int messageVersion) {
        super(messageVersion);
        this.envelopes = envelopes;
    }


    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setEnvelopeOfEnvelopes(PB.EnvelopeOfEnvelopes.newBuilder().addAllEnvelopes(envelopes.stream()
                        .map(NetworkEnvelope::toProtoNetworkEnvelope)
                        .collect(Collectors.toList())))
                .build();
    }

    public static EnvelopeOfEnvelopes fromProto(PB.EnvelopeOfEnvelopes proto, NetworkProtoResolver resolver, int messageVersion) {
        List<NetworkEnvelope> envelopes = proto.getEnvelopesList()
                .stream()
                .map(envelope -> {
                    try {
                        return resolver.fromProto(envelope);
                    } catch (ProtobufferException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new EnvelopeOfEnvelopes(envelopes, messageVersion);
    }
}

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

import bisq.network.p2p.storage.messages.RefreshOfferMessage;

import bisq.common.app.Version;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkProtoResolver;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BundleOfEnvelopesTest {
    @BeforeEach
    void setUp() {
        // Envelopes compare their message version, which must be set
        Version.setBaseCryptoNetworkId(1);
    }

    @Test
    void countsTheElementsWhichCannotBeParsed() throws ProtobufferException {
        NetworkProtoResolver resolver = mock(NetworkProtoResolver.class);
        RefreshOfferMessage valid = new RefreshOfferMessage(new byte[20], new byte[46], new byte[20], 1);
        protobuf.NetworkEnvelope unknown = protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .build();
        when(resolver.fromProto(valid.toProtoNetworkEnvelope())).thenReturn(valid);
        when(resolver.fromProto(unknown)).thenThrow(new ProtobufferException("Unknown proto message case"));

        BundleOfEnvelopes bundle = BundleOfEnvelopes.fromProto(protobuf.BundleOfEnvelopes.newBuilder()
                        .addEnvelopes(unknown)
                        .addEnvelopes(valid.toProtoNetworkEnvelope())
                        .addEnvelopes(unknown)
                        .build(),
                resolver,
                Version.getP2PMessageVersion());

        assertEquals(List.of(valid), bundle.getEnvelopes());
        assertEquals(2, bundle.getNumUnparsableEnvelopes());
        // The count is no part of the message
        BundleOfEnvelopes sent = new BundleOfEnvelopes(List.of(valid));
        assertEquals(sent, bundle);
        assertEquals(sent.toProtoNetworkEnvelope(), bundle.toProtoNetworkEnvelope());
    }
}

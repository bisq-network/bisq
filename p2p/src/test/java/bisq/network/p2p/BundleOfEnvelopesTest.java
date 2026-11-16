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

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;

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
        // A message of a known type which the resolver cannot convert, as one of a type which was removed
        @SuppressWarnings("deprecation")
        protobuf.NetworkEnvelope invalid = protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setFileTransferPart(protobuf.FileTransferPart.newBuilder())
                .build();
        when(resolver.fromProto(valid.toProtoNetworkEnvelope())).thenReturn(valid);
        when(resolver.fromProto(invalid)).thenThrow(new ProtobufferException("Unknown proto message case"));

        BundleOfEnvelopes bundle = BundleOfEnvelopes.fromProto(protobuf.BundleOfEnvelopes.newBuilder()
                        .addEnvelopes(invalid)
                        .addEnvelopes(valid.toProtoNetworkEnvelope())
                        .addEnvelopes(invalid)
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

    @Test
    void dropsTheElementsOfAnUnknownTypeWithoutCountingThem() throws ProtobufferException {
        NetworkProtoResolver resolver = mock(NetworkProtoResolver.class);
        RefreshOfferMessage valid = new RefreshOfferMessage(new byte[20], new byte[46], new byte[20], 1);
        // A message of a type which the node does not know, as one of a newer version. Its field is unknown, so no
        // message type is set.
        protobuf.NetworkEnvelope unknown = protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setUnknownFields(UnknownFieldSet.newBuilder()
                        .addField(1000, UnknownFieldSet.Field.newBuilder()
                                .addLengthDelimited(ByteString.copyFromUtf8("message of a newer version"))
                                .build())
                        .build())
                .build();
        when(resolver.fromProto(valid.toProtoNetworkEnvelope())).thenReturn(valid);
        // As the resolver of the node does
        when(resolver.fromProto(unknown)).thenThrow(new ProtobufferException("Unknown proto message case"));

        BundleOfEnvelopes bundle = BundleOfEnvelopes.fromProto(protobuf.BundleOfEnvelopes.newBuilder()
                        .addEnvelopes(unknown)
                        .addEnvelopes(valid.toProtoNetworkEnvelope())
                        .build(),
                resolver,
                Version.getP2PMessageVersion());

        assertEquals(List.of(valid), bundle.getEnvelopes());
        assertEquals(0, bundle.getNumUnparsableEnvelopes());
    }
}

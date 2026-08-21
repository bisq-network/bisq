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

package bisq.core.dao.governance.blindvote.storage;

import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.proto.network.CoreNetworkProtoResolver;

import bisq.network.p2p.BundleOfEnvelopes;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.payload.InvalidPersistableNetworkPayloadException;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Version;
import bisq.common.proto.ProtobufferException;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlindVotePayloadTest {
    @Test
    public void fromProtoThrowsIfHashDoesNotMatchBlindVoteData() {
        BlindVotePayload payload = new BlindVotePayload(blindVote("blind-vote-tx"));

        assertThrows(InvalidPersistableNetworkPayloadException.class,
                () -> BlindVotePayload.fromProto(withWrongHash(payload)));
    }

    @Test
    public void blindVoteStoreFromProtoSkipsOnlyInvalidBlindVotePayloads() {
        BlindVotePayload validPayload = new BlindVotePayload(blindVote("valid-blind-vote-tx"));
        BlindVotePayload invalidPayload = new BlindVotePayload(blindVote("invalid-blind-vote-tx"));
        protobuf.BlindVoteStore proto = protobuf.BlindVoteStore.newBuilder()
                .addItems(validPayload.toProtoBlindVotePayload())
                .addItems(withWrongHash(invalidPayload))
                .build();

        BlindVoteStore blindVoteStore = BlindVoteStore.fromProto(proto);

        assertEquals(1, blindVoteStore.getMap().size());
        assertTrue(blindVoteStore.containsKey(new P2PDataStorage.ByteArray(validPayload.getHash())));
    }

    @Test
    public void getDataResponseFromProtoSkipsOnlyInvalidBlindVotePayloads() {
        BlindVotePayload validPayload = new BlindVotePayload(blindVote("valid-blind-vote-tx"));
        BlindVotePayload invalidPayload = new BlindVotePayload(blindVote("invalid-blind-vote-tx"));
        protobuf.GetDataResponse proto = protobuf.GetDataResponse.newBuilder()
                .addPersistableNetworkPayloadItems(toPersistableNetworkPayload(validPayload.toProtoBlindVotePayload()))
                .addPersistableNetworkPayloadItems(toPersistableNetworkPayload(withWrongHash(invalidPayload)))
                .build();

        GetDataResponse getDataResponse = GetDataResponse.fromProto(proto,
                new CoreNetworkProtoResolver(Clock.systemUTC()),
                Version.getP2PMessageVersion());

        Set<PersistableNetworkPayload> payloads = getDataResponse.getPersistableNetworkPayloadSet();
        assertEquals(1, payloads.size());
        BlindVotePayload blindVotePayload = (BlindVotePayload) payloads.iterator().next();
        assertEquals("valid-blind-vote-tx", blindVotePayload.getBlindVote().getTxId());
        assertArrayEquals(validPayload.getHash(), blindVotePayload.getHash());
    }

    @Test
    public void addPersistableNetworkPayloadMessageFromProtoWrapsInvalidBlindVotePayload() {
        BlindVotePayload invalidPayload = new BlindVotePayload(blindVote("invalid-blind-vote-tx"));
        protobuf.AddPersistableNetworkPayloadMessage proto = protobuf.AddPersistableNetworkPayloadMessage.newBuilder()
                .setPayload(toPersistableNetworkPayload(withWrongHash(invalidPayload)))
                .build();

        assertThrows(ProtobufferException.class,
                () -> AddPersistableNetworkPayloadMessage.fromProto(proto,
                        new CoreNetworkProtoResolver(Clock.systemUTC()),
                        Version.getP2PMessageVersion()));
    }

    @Test
    public void bundleOfEnvelopesFromProtoSkipsInvalidAddPersistableNetworkPayloadMessage() {
        BlindVotePayload validPayload = new BlindVotePayload(blindVote("valid-blind-vote-tx"));
        BlindVotePayload invalidPayload = new BlindVotePayload(blindVote("invalid-blind-vote-tx"));
        protobuf.NetworkEnvelope invalidEnvelope = protobuf.NetworkEnvelope.newBuilder()
                .setMessageVersion(Version.getP2PMessageVersion())
                .setAddPersistableNetworkPayloadMessage(protobuf.AddPersistableNetworkPayloadMessage.newBuilder()
                        .setPayload(toPersistableNetworkPayload(withWrongHash(invalidPayload))))
                .build();
        protobuf.BundleOfEnvelopes proto = protobuf.BundleOfEnvelopes.newBuilder()
                .addEnvelopes(invalidEnvelope)
                .addEnvelopes(new AddPersistableNetworkPayloadMessage(validPayload).toProtoNetworkEnvelope())
                .build();

        BundleOfEnvelopes bundle = BundleOfEnvelopes.fromProto(proto,
                new CoreNetworkProtoResolver(Clock.systemUTC()),
                Version.getP2PMessageVersion());

        assertEquals(1, bundle.getEnvelopes().size());
        assertTrue(bundle.getEnvelopes().get(0) instanceof AddPersistableNetworkPayloadMessage);
    }

    private static BlindVote blindVote(String txId) {
        return new BlindVote(new byte[]{0x01, 0x02, 0x03},
                txId,
                123_456,
                new byte[]{0x04, 0x05},
                1_700_000_000_000L);
    }

    private static protobuf.BlindVotePayload withWrongHash(BlindVotePayload payload) {
        byte[] wrongHash = payload.getHash().clone();
        wrongHash[0] ^= 1;
        return payload.toProtoBlindVotePayload().toBuilder()
                .setHash(ByteString.copyFrom(wrongHash))
                .build();
    }

    private static protobuf.PersistableNetworkPayload toPersistableNetworkPayload(protobuf.BlindVotePayload blindVotePayload) {
        return protobuf.PersistableNetworkPayload.newBuilder()
                .setBlindVotePayload(blindVotePayload)
                .build();
    }
}

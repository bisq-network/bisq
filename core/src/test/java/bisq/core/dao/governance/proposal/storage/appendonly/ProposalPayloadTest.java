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

package bisq.core.dao.governance.proposal.storage.appendonly;

import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.Proposal;
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
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProposalPayloadTest {
    @Test
    public void fromProtoThrowsIfHashDoesNotMatchProposalData() {
        ProposalPayload payload = new ProposalPayload(proposal("proposal-tx"));

        assertThrows(InvalidPersistableNetworkPayloadException.class,
                () -> ProposalPayload.fromProto(withWrongHash(payload)));
    }

    @Test
    public void proposalStoreFromProtoSkipsOnlyInvalidProposalPayloads() {
        ProposalPayload validPayload = new ProposalPayload(proposal("valid-proposal-tx"));
        ProposalPayload invalidPayload = new ProposalPayload(proposal("invalid-proposal-tx"));
        protobuf.ProposalStore proto = protobuf.ProposalStore.newBuilder()
                .addItems(validPayload.toProtoProposalPayload())
                .addItems(withWrongHash(invalidPayload))
                .build();

        ProposalStore proposalStore = ProposalStore.fromProto(proto);

        assertEquals(1, proposalStore.getMap().size());
        assertTrue(proposalStore.containsKey(new P2PDataStorage.ByteArray(validPayload.getHash())));
    }

    @Test
    public void getDataResponseFromProtoSkipsOnlyInvalidProposalPayloads() {
        ProposalPayload validPayload = new ProposalPayload(proposal("valid-proposal-tx"));
        ProposalPayload invalidPayload = new ProposalPayload(proposal("invalid-proposal-tx"));
        protobuf.GetDataResponse proto = protobuf.GetDataResponse.newBuilder()
                .addPersistableNetworkPayloadItems(toPersistableNetworkPayload(validPayload.toProtoProposalPayload()))
                .addPersistableNetworkPayloadItems(toPersistableNetworkPayload(withWrongHash(invalidPayload)))
                .build();

        GetDataResponse getDataResponse = GetDataResponse.fromProto(proto,
                new CoreNetworkProtoResolver(Clock.systemUTC()),
                Version.getP2PMessageVersion());

        Set<PersistableNetworkPayload> payloads = getDataResponse.getPersistableNetworkPayloadSet();
        assertEquals(1, payloads.size());
        ProposalPayload proposalPayload = (ProposalPayload) payloads.iterator().next();
        assertEquals("valid-proposal-tx", proposalPayload.getProposal().getTxId());
        assertArrayEquals(validPayload.getHash(), proposalPayload.getHash());
    }

    @Test
    public void addPersistableNetworkPayloadMessageFromProtoWrapsInvalidProposalPayload() {
        ProposalPayload invalidPayload = new ProposalPayload(proposal("invalid-proposal-tx"));
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
        ProposalPayload validPayload = new ProposalPayload(proposal("valid-proposal-tx"));
        ProposalPayload invalidPayload = new ProposalPayload(proposal("invalid-proposal-tx"));
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

    private static Proposal proposal(String txId) {
        return new GenericProposal("name-" + txId, "https://bisq.network/" + txId, new TreeMap<>())
                .cloneProposalAndAddTxId(txId);
    }

    private static protobuf.ProposalPayload withWrongHash(ProposalPayload payload) {
        byte[] wrongHash = payload.getHash().clone();
        wrongHash[0] ^= 1;
        return payload.toProtoProposalPayload().toBuilder()
                .setHash(ByteString.copyFrom(wrongHash))
                .build();
    }

    private static protobuf.PersistableNetworkPayload toPersistableNetworkPayload(protobuf.ProposalPayload proposalPayload) {
        return protobuf.PersistableNetworkPayload.newBuilder()
                .setProposalPayload(proposalPayload)
                .build();
    }
}

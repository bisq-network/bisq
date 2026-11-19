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

package bisq.core.dao.governance.proposal.storage.temp;

import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.proto.network.CoreNetworkProtoResolver;

import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.crypto.Sig;
import bisq.common.proto.network.NetworkProtoResolver;

import com.google.protobuf.ByteString;

import java.security.KeyPair;

import java.time.Clock;

import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TempProposalStoreTest {
    // The entries come from the network. A single one we cannot deserialize must not cost us the whole store,
    // because PersistenceManager treats a throw from here as a corrupted file and moves it aside.
    @Test
    public void fromProtoSkipsOnlyTheEntryItCannotDeserialize() {
        NetworkProtoResolver resolver = new CoreNetworkProtoResolver(Clock.systemUTC());
        protobuf.ProtectedStorageEntry valid = entry("valid-proposal-tx");
        protobuf.ProtectedStorageEntry withUnusableOwnerPubKey = entry("poisoned-proposal-tx").toBuilder()
                .setOwnerPubKeyBytes(ByteString.copyFrom(new byte[Sig.MAX_SIG_PUB_KEY_BYTES + 1]))
                .build();

        TempProposalStore store = TempProposalStore.fromProto(protobuf.TempProposalStore.newBuilder()
                .addItems(withUnusableOwnerPubKey)
                .addItems(valid)
                .build(), resolver);

        assertEquals(1, store.getMap().size());
        ProtectedStorageEntry kept = store.getMap().values().iterator().next();
        assertTrue(kept.getProtectedStoragePayload() instanceof TempProposalPayload);
        assertEquals("valid-proposal-tx",
                ((TempProposalPayload) kept.getProtectedStoragePayload()).getProposal().getTxId());
    }

    private static protobuf.ProtectedStorageEntry entry(String txId) {
        KeyPair ownerKeyPair = Sig.generateKeyPair();
        TempProposalPayload payload = new TempProposalPayload(proposal(txId), ownerKeyPair.getPublic());
        return protobuf.ProtectedStorageEntry.newBuilder()
                .setStoragePayload((protobuf.StoragePayload) payload.toProtoMessage())
                .setOwnerPubKeyBytes(ByteString.copyFrom(Sig.getPublicKeyBytes(ownerKeyPair.getPublic())))
                .setSequenceNumber(1)
                .setSignature(ByteString.copyFrom(new byte[]{1, 2, 3}))
                .setCreationTimeStamp(System.currentTimeMillis())
                .build();
    }

    private static Proposal proposal(String txId) {
        TreeMap<String, String> extraDataMap = new TreeMap<>();
        extraDataMap.put("key", "value");
        return new GenericProposal("name-" + txId, "https://bisq.network/" + txId, extraDataMap)
                .cloneProposal(txId);
    }
}

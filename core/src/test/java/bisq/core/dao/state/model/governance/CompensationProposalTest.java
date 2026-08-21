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

package bisq.core.dao.state.model.governance;

import bisq.common.util.ExtraDataMapValidator;

import org.bitcoinj.core.Coin;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompensationProposalTest {
    @Test
    public void singleEntryExtraDataMapSerializesLikeHashMap() {
        TreeMap<String, String> extraDataMap = new TreeMap<>();
        extraDataMap.put(CompensationProposal.BURNING_MAN_RECEIVER_ADDRESS, "receiverAddress");

        CompensationProposal proposal = new CompensationProposal(
                "name",
                "link",
                Coin.valueOf(10_000),
                "bsqAddress",
                extraDataMap);

        protobuf.Proposal treeMapProto = proposal.toProtoMessage();
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(CompensationProposal.BURNING_MAN_RECEIVER_ADDRESS, "receiverAddress");
        protobuf.Proposal hashMapProto = treeMapProto.toBuilder()
                .clearExtraData()
                .putAllExtraData(hashMap)
                .build();

        assertArrayEquals(hashMapProto.toByteArray(), treeMapProto.toByteArray());
    }

    @Test
    public void fromProtoConvertsExtraDataMapToTreeMapWithoutChangingSingleEntryBytes() {
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(CompensationProposal.BURNING_MAN_RECEIVER_ADDRESS, "receiverAddress");
        protobuf.Proposal hashMapProto = protobuf.Proposal.newBuilder()
                .setName("name")
                .setLink("link")
                .setVersion(1)
                .setCreationDate(1)
                .setTxId("txId")
                .setCompensationProposal(protobuf.CompensationProposal.newBuilder()
                        .setRequestedBsq(10_000)
                        .setBsqAddress("bsqAddress"))
                .putAllExtraData(hashMap)
                .build();

        CompensationProposal proposal = CompensationProposal.fromProto(hashMapProto);

        assertTrue(proposal.getExtraDataMap() instanceof TreeMap);
        assertArrayEquals(hashMapProto.toByteArray(), proposal.toProtoMessage().toByteArray());
    }

    @Test
    public void fromProtoSanitizesInvalidExtraDataMap() {
        Map<String, String> oversizedMap = new HashMap<>();
        for (int i = 0; i <= ExtraDataMapValidator.MAX_SIZE; i++) {
            oversizedMap.put("key" + i, "value");
        }
        protobuf.Proposal proto = protobuf.Proposal.newBuilder()
                .setName("name")
                .setLink("link")
                .setVersion(1)
                .setCreationDate(1)
                .setTxId("txId")
                .setCompensationProposal(protobuf.CompensationProposal.newBuilder()
                        .setRequestedBsq(10_000)
                        .setBsqAddress("bsqAddress"))
                .putAllExtraData(oversizedMap)
                .build();

        CompensationProposal proposal = CompensationProposal.fromProto(proto);

        Map<String, String> extraDataMap = proposal.getExtraDataMap();
        assertNotNull(extraDataMap);
        assertTrue(extraDataMap instanceof TreeMap);
        assertTrue(extraDataMap.isEmpty());
        assertTrue(proposal.toProtoMessage().getExtraDataMap().isEmpty());
    }
}

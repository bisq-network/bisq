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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.state.model;

import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalMapEntryByteCache;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.encoding.canonical.LegacyCollectorsToMapIterator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaoStateCanonicalEncoderTest {
    private static final int RANDOMIZED_TX_OUTPUT_KEY_COUNT = 50_000;
    private static final String JAVA_11_RANDOMIZED_TX_OUTPUT_KEY_ORDER_DIGEST =
            "33ef83bb099ad2ccbcb7ac588b664c8be6770fa71034ea25cec5ce7aef4fb84f";
    private static final List<String> KEYS = IntStream.range(0, 40)
            .mapToObj(i -> String.format("k%02d", i))
            .collect(Collectors.toList());
    private static final List<String> TX_OUTPUT_KEY_TREE_MAP_ORDER = KEYS.stream()
            .map(key -> key + ":0")
            .collect(Collectors.toList());
    private static final List<String> JAVA_11_HASH_MAP_ORDER = List.of(
            "k31", "k30", "k11", "k33", "k10", "k32", "k13", "k35", "k12", "k34",
            "k15", "k37", "k14", "k36", "k17", "k39", "k16", "k38", "k19", "k18",
            "k20", "k00", "k22", "k21", "k02", "k24", "k01", "k23", "k04", "k26",
            "k03", "k25", "k06", "k28", "k05", "k27", "k08", "k07", "k29", "k09");
    private static final List<String> JAVA_11_TX_OUTPUT_KEY_HASH_MAP_ORDER = List.of(
            "k31:0", "k30:0", "k09:0", "k07:0", "k08:0", "k29:0", "k01:0", "k24:0",
            "k02:0", "k23:0", "k22:0", "k00:0", "k21:0", "k05:0", "k28:0", "k06:0",
            "k27:0", "k03:0", "k26:0", "k04:0", "k25:0", "k20:0", "k18:0", "k19:0",
            "k12:0", "k35:0", "k13:0", "k34:0", "k10:0", "k33:0", "k11:0", "k32:0",
            "k16:0", "k39:0", "k17:0", "k38:0", "k14:0", "k37:0", "k15:0", "k36:0");
    private static final List<String> JAVA_11_TREEIFIED_COLLISION_HASH_MAP_ORDER = List.of(
            "AaAaBBBB", "AaAaAaAa", "AaAaAaBB", "AaAaBBAa", "AaBBAaAa", "AaBBAaBB",
            "AaBBBBAa", "AaBBBBBB", "BBAaAaAa", "BBAaAaBB", "BBAaBBAa", "BBAaBBBB",
            "BBBBAaAa", "BBBBAaBB", "BBBBBBAa", "BBBBBBBB");

    @Test
    void returnsJava11HashMapIterationOrder() {
        assertEquals(JAVA_11_HASH_MAP_ORDER,
                LegacyCollectorsToMapIterator.getJava11HashMapIterationOrder(KEYS));
    }

    @Test
    void preservesJava11HashMapOrderForListHashCollisions() {
        List<String> collidingKeys = getSameHashStrings(3);

        assertEquals(1, collidingKeys.stream().mapToInt(String::hashCode).distinct().count());
        assertEquals(collidingKeys, LegacyCollectorsToMapIterator.getJava11HashMapIterationOrder(collidingKeys));
    }

    @Test
    void preservesJava11HashMapOrderForTreeifiedHashCollisions() {
        List<String> collidingKeys = getSameHashStrings(4);

        assertEquals(1, collidingKeys.stream().mapToInt(String::hashCode).distinct().count());
        assertEquals(JAVA_11_TREEIFIED_COLLISION_HASH_MAP_ORDER,
                LegacyCollectorsToMapIterator.getJava11HashMapIterationOrder(collidingKeys));
    }

    @Test
    void preservesJava11HashMapOrderForMassiveRandomizedTxOutputKeys() throws Exception {
        List<String> keys = getRandomizedTxOutputKeyStrings(RANDOMIZED_TX_OUTPUT_KEY_COUNT);
        List<String> orderedKeys = LegacyCollectorsToMapIterator.getJava11HashMapIterationOrder(keys);

        assertEquals(RANDOMIZED_TX_OUTPUT_KEY_COUNT, orderedKeys.size());
        assertEquals(JAVA_11_RANDOMIZED_TX_OUTPUT_KEY_ORDER_DIGEST, getOrderDigest(orderedKeys));
    }

    @Test
    void serializesNormalDaoStateMapsInTreeMapOrder() {
        DaoState daoState = getDaoStateWithMaps();

        protobuf.DaoState proto = DaoState.getBsqStateCloneExcludingBlocks(daoState);

        assertEquals(KEYS, new ArrayList<>(proto.getIssuanceMapMap().keySet()));
        assertEquals(TX_OUTPUT_KEY_TREE_MAP_ORDER,
                new ArrayList<>(proto.getUnspentTxOutputMapMap().keySet()));
        assertEquals(TX_OUTPUT_KEY_TREE_MAP_ORDER,
                new ArrayList<>(proto.getSpentInfoMapMap().keySet()));
    }

    @Test
    void serializesFullDaoStateMapsInTreeMapOrder() {
        DaoState daoState = getDaoStateWithMaps();

        protobuf.DaoState proto = (protobuf.DaoState) daoState.toProtoMessage();

        assertEquals(2, proto.getBlocksCount());
        assertEquals(KEYS, new ArrayList<>(proto.getIssuanceMapMap().keySet()));
        assertEquals(TX_OUTPUT_KEY_TREE_MAP_ORDER,
                new ArrayList<>(proto.getUnspentTxOutputMapMap().keySet()));
        assertEquals(TX_OUTPUT_KEY_TREE_MAP_ORDER,
                new ArrayList<>(proto.getSpentInfoMapMap().keySet()));
    }

    @Test
    void spentInfoMapOptsIntoCanonicalMapEntryCaching() {
        DaoState daoState = getDaoStateWithMaps();
        DaoState fromProto = DaoState.fromProto((protobuf.DaoState) daoState.toProtoMessage());

        assertTrue(daoState.getSpentInfoMap() instanceof CanonicalMapEntryByteCache);
        assertTrue(fromProto.getSpentInfoMap() instanceof CanonicalMapEntryByteCache);
    }

    @Test
    void genericCanonicalEncodingIncludesFullBlockList() throws Exception {
        DaoState daoState = getDaoStateWithMaps();

        protobuf.DaoState proto = protobuf.DaoState.parseFrom(daoState.encodeCanonical(CanonicalEncoder.DEFAULT));

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), fieldNumbers(DaoState.SCHEMA));
        assertEquals(2, proto.getBlocksCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void spentInfoMapCachesAndInvalidatesEncodedMapField() {
        DaoState daoState = getDaoStateWithMaps();
        // DaoState maps TxOutputKey to its canonical string form before the encoder uses the map-entry cache.
        CanonicalMapEntryByteCache<String, SpentInfo> cache =
                (CanonicalMapEntryByteCache<String, SpentInfo>) daoState.getSpentInfoMap();
        CanonicalSchema.Field<DaoState> spentInfoMapField = DaoState.STATE_HASH_CHAIN_SCHEMA.getFields().stream()
                .filter(field -> field.getNumber() == 7)
                .findFirst()
                .orElseThrow();

        assertNull(cache.getEncodedMap(spentInfoMapField));

        daoState.getSerializedStateForHashChain();

        assertNotNull(cache.getEncodedMap(spentInfoMapField));

        daoState.getSpentInfoMap().put(new TxOutputKey("changed", 0), new SpentInfo(101, "changed", 0));

        assertNull(cache.getEncodedMap(spentInfoMapField));
    }

    @Test
    void spentInfoMapInvalidatesEncodedMapFieldForEntrySetIteratorRemove() throws Exception {
        DaoState daoState = getDaoStateWithMaps();
        int initialCount = protobuf.DaoState.parseFrom(daoState.getSerializedStateForHashChain())
                .getSpentInfoMapCount();

        var iterator = daoState.getSpentInfoMap().entrySet().iterator();
        iterator.next();
        iterator.remove();

        protobuf.DaoState proto = protobuf.DaoState.parseFrom(daoState.getSerializedStateForHashChain());

        assertEquals(initialCount - 1, proto.getSpentInfoMapCount());
    }

    @Test
    void spentInfoMapInvalidatesEncodedMapFieldForSubMapClear() throws Exception {
        DaoState daoState = getDaoStateWithMaps();
        int initialCount = protobuf.DaoState.parseFrom(daoState.getSerializedStateForHashChain())
                .getSpentInfoMapCount();

        daoState.getSpentInfoMap()
                .subMap(new TxOutputKey("k00", 0), true, new TxOutputKey("k02", 0), true)
                .clear();

        protobuf.DaoState proto = protobuf.DaoState.parseFrom(daoState.getSerializedStateForHashChain());

        assertEquals(initialCount - 3, proto.getSpentInfoMapCount());
    }

    @Test
    void serializesDaoStateMapsInJava11HashMapIterationOrder() throws Exception {
        DaoState daoState = getDaoStateWithMaps();

        protobuf.DaoState proto = protobuf.DaoState.parseFrom(daoState.getSerializedStateForHashChain());

        assertEquals(JAVA_11_HASH_MAP_ORDER, new ArrayList<>(proto.getIssuanceMapMap().keySet()));
        assertEquals(JAVA_11_TX_OUTPUT_KEY_HASH_MAP_ORDER,
                new ArrayList<>(proto.getUnspentTxOutputMapMap().keySet()));
        assertEquals(JAVA_11_TX_OUTPUT_KEY_HASH_MAP_ORDER,
                new ArrayList<>(proto.getSpentInfoMapMap().keySet()));
    }

    @Test
    void serializesLegacyDaoStateMapsInJava11HashMapIterationOrder() throws Exception {
        DaoState daoState = getDaoStateWithMaps();

        protobuf.DaoState proto = protobuf.DaoState.parseFrom(daoState.getSerializedStateForHashChainLegacy());

        assertEquals(JAVA_11_HASH_MAP_ORDER, new ArrayList<>(proto.getIssuanceMapMap().keySet()));
        assertEquals(JAVA_11_TX_OUTPUT_KEY_HASH_MAP_ORDER,
                new ArrayList<>(proto.getUnspentTxOutputMapMap().keySet()));
        assertEquals(JAVA_11_TX_OUTPUT_KEY_HASH_MAP_ORDER,
                new ArrayList<>(proto.getSpentInfoMapMap().keySet()));
    }

    @Test
    void serializesEmptyDaoStateCloneExcludingBlocksForGenesisResync() {
        protobuf.DaoState proto = DaoState.getBsqStateCloneExcludingBlocks(new DaoState());

        assertEquals(0, proto.getChainHeight());
        assertEquals(0, proto.getBlocksCount());
        assertEquals(0, proto.getCyclesCount());
        assertEquals(0, proto.getUnspentTxOutputMapCount());
        assertEquals(0, proto.getSpentInfoMapCount());
        assertEquals(0, proto.getConfiscatedLockupTxListCount());
        assertEquals(0, proto.getIssuanceMapCount());
        assertEquals(0, proto.getParamChangeListCount());
        assertEquals(0, proto.getEvaluatedProposalListCount());
        assertEquals(0, proto.getDecryptedBallotsWithMeritsListCount());
        assertArrayEquals(proto.toByteArray(),
                DaoState.getBsqStateCloneExcludingBlocks(DaoState.fromProto(proto)).toByteArray());
    }

    @Test
    void serializesLegacyEmptyDaoStateHashChainWithoutLastBlock() throws Exception {
        protobuf.DaoState proto = protobuf.DaoState.parseFrom(new DaoState().getSerializedStateForHashChainLegacy());

        assertEquals(0, proto.getBlocksCount());
    }

    @Test
    void serializesEmptyDaoStateForHashChainWithoutLastBlock() throws Exception {
        protobuf.DaoState proto = protobuf.DaoState.parseFrom(new DaoState().getSerializedStateForHashChain());

        assertEquals(0, proto.getChainHeight());
        assertEquals(0, proto.getBlocksCount());
    }

    @Test
    void hashChainSerializationUsesCanonicalEncoderAndOnlyLastBlock() throws Exception {
        DaoState daoState = getDaoStateWithMaps();

        byte[] serializedStateForHashChain = daoState.getSerializedStateForHashChain();
        protobuf.DaoState proto = protobuf.DaoState.parseFrom(serializedStateForHashChain);

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                fieldNumbers(DaoState.STATE_HASH_CHAIN_SCHEMA));
        assertArrayEquals(daoState.encodeCanonicalForStateHashChain(CanonicalEncoder.DEFAULT),
                serializedStateForHashChain);
        assertEquals(1, proto.getBlocksCount());
        assertEquals(100, proto.getBlocks(0).getHeight());
    }

    private static DaoState getDaoStateWithMaps() {
        DaoState daoState = new DaoState();
        daoState.setChainHeight(100);
        daoState.addBlock(new Block(99, 900, "previousBlockHash", "olderBlockHash"));
        daoState.addBlock(new Block(100, 1_000, "blockHash", "previousBlockHash"));

        for (int i = 0; i < KEYS.size(); i++) {
            String key = KEYS.get(i);
            daoState.getIssuanceMap().put(key,
                    new Issuance(key, 100, i, "pubKey" + i, IssuanceType.COMPENSATION));

            TxOutputKey txOutputKey = new TxOutputKey(key, 0);
            daoState.getUnspentTxOutputMap().put(txOutputKey, getTxOutput(key, i));
            daoState.getSpentInfoMap().put(txOutputKey, new SpentInfo(100, key, i));
        }
        return daoState;
    }

    private static TxOutput getTxOutput(String txId, int index) {
        protobuf.BaseTxOutput proto = protobuf.BaseTxOutput.newBuilder()
                .setIndex(0)
                .setValue(1000 + index)
                .setTxId(txId)
                .setBlockHeight(100)
                .setTxOutput(protobuf.TxOutput.newBuilder()
                        .setTxOutputType(protobuf.TxOutputType.BSQ_OUTPUT)
                        .setLockTime(-1)
                        .setUnlockBlockHeight(0))
                .build();
        return TxOutput.fromProto(proto);
    }

    private static List<String> getSameHashStrings(int parts) {
        List<String> result = new ArrayList<>();
        int count = 1 << parts;
        for (int i = 0; i < count; i++) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int bit = parts - 1; bit >= 0; bit--) {
                stringBuilder.append(((i >>> bit) & 1) == 0 ? "Aa" : "BB");
            }
            result.add(stringBuilder.toString());
        }
        Collections.sort(result);
        return result;
    }

    private static List<String> getRandomizedTxOutputKeyStrings(int count) {
        List<String> keys = new ArrayList<>(count);
        long state = 0x6a09e667f3bcc909L;
        for (int i = 0; i < count; i++) {
            StringBuilder txId = new StringBuilder(64);
            for (int part = 0; part < 4; part++) {
                state = state * 0x5851f42d4c957f2dL + 0x14057b7ef767814fL;
                txId.append(toPaddedHex(state));
            }
            keys.add(txId + ":" + (i & 3));
        }
        Collections.sort(keys);
        return keys;
    }

    private static String toPaddedHex(long value) {
        String hex = Long.toHexString(value);
        return "0000000000000000".substring(hex.length()) + hex;
    }

    private static String getOrderDigest(List<String> keys) throws Exception {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        for (String key : keys) {
            messageDigest.update(key.getBytes(StandardCharsets.UTF_8));
            messageDigest.update((byte) 0);
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : messageDigest.digest()) {
            stringBuilder.append(String.format("%02x", b));
        }
        return stringBuilder.toString();
    }

    private static List<Integer> fieldNumbers(CanonicalSchema<?> schema) {
        return schema.getFields().stream()
                .map(CanonicalSchema.Field::getNumber)
                .collect(Collectors.toList());
    }
}

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

package bisq.core.dao.monitoring.serialization;

import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.ParamChange;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reproduces the byte stream that
 * {@code DaoState.getSerializedStateForHashChain()} produced before this
 * refactor, exactly, for every block on the chain.
 *
 * <p>The historical pipeline was:
 * <pre>
 *   TreeMap&lt;K,V&gt;                       (the field, sorted by key)
 *     .entrySet().stream()
 *     .collect(Collectors.toMap(...))      // intermediate java.util.HashMap
 *     putAll...Map(hashMap)                // protobuf MapFieldLite (LinkedHashMap)
 *     .build().toByteArray()
 * </pre>
 *
 * <p>The intermediate {@code java.util.HashMap}'s bucket layout determines
 * the emitted map field byte order, so the bytes depend on the JDK's
 * {@code HashMap} implementation. To remove that fragility we replace the
 * intermediate {@code HashMap} with {@link PinnedHashMap} — a frozen copy of
 * OpenJDK 11's {@code java.util.HashMap}. The protobuf side (insertion-order
 * preserving {@code MapFieldLite}) and the non-deterministic
 * {@code toByteArray()} stay unchanged, so the emitted bytes are
 * byte-for-byte identical to the legacy path on JDK 11, and on every future
 * JDK regardless of changes to {@code java.util.HashMap}.
 */
public class LegacyProtobufDaoStateSerializer implements DaoStateHashSerializer {

    @Override
    public byte[] serialize(DaoState daoState) {
        protobuf.DaoState.Builder builder = protobuf.DaoState.newBuilder();
        builder.setChainHeight(daoState.getChainHeight())
                .addAllCycles(daoState.getCycles().stream()
                        .map(Cycle::toProtoMessage)
                        .collect(Collectors.toList()))
                .putAllUnspentTxOutputMap(toPinnedHashMap(
                        daoState.getUnspentTxOutputMap(),
                        e -> e.getKey().toString(),
                        e -> e.getValue().toProtoMessage()))
                .putAllSpentInfoMap(toPinnedHashMap(
                        daoState.getSpentInfoMap(),
                        e -> e.getKey().toString(),
                        e -> e.getValue().toProtoMessage()))
                .addAllConfiscatedLockupTxList(daoState.getConfiscatedLockupTxList())
                .putAllIssuanceMap(toPinnedHashMap(
                        daoState.getIssuanceMap(),
                        Map.Entry::getKey,
                        e -> e.getValue().toProtoMessage()))
                .addAllParamChangeList(daoState.getParamChangeList().stream()
                        .map(ParamChange::toProtoMessage)
                        .collect(Collectors.toList()))
                .addAllEvaluatedProposalList(daoState.getEvaluatedProposalList().stream()
                        .map(EvaluatedProposal::toProtoMessage)
                        .collect(Collectors.toList()))
                .addAllDecryptedBallotsWithMeritsList(daoState.getDecryptedBallotsWithMeritsList().stream()
                        .map(DecryptedBallotsWithMerits::toProtoMessage)
                        .collect(Collectors.toList()));

        return builder.addBlocks(daoState.getLastBlock().toProtoMessage()).build().toByteArray();
    }

    /**
     * Replicates {@code Collectors.toMap(keyMapper, valueMapper)} but with the
     * intermediate map type pinned to {@link PinnedHashMap} (a vendored copy
     * of OpenJDK 11's {@code HashMap}). The accumulation pattern —
     * default-capacity supplier + per-element {@code putIfAbsent} + duplicate
     * detection — matches {@code Collectors#uniqKeysMapAccumulator}, so the
     * resulting bucket layout, and hence iteration order, is the same as the
     * legacy path's intermediate HashMap on JDK 11.
     */
    private static <K_IN, V_IN, K_OUT, V_OUT> PinnedHashMap<K_OUT, V_OUT> toPinnedHashMap(
            java.util.TreeMap<K_IN, V_IN> source,
            java.util.function.Function<Map.Entry<K_IN, V_IN>, K_OUT> keyMapper,
            java.util.function.Function<Map.Entry<K_IN, V_IN>, V_OUT> valueMapper) {
        PinnedHashMap<K_OUT, V_OUT> out = new PinnedHashMap<>();
        for (Map.Entry<K_IN, V_IN> e : source.entrySet()) {
            K_OUT k = keyMapper.apply(e);
            V_OUT v = java.util.Objects.requireNonNull(valueMapper.apply(e));
            V_OUT prev = out.putIfAbsent(k, v);
            if (prev != null) {
                throw new IllegalStateException("Duplicate key " + k + " (attempted merging values " + prev + " and " + v + ")");
            }
        }
        return out;
    }

    // Suppress unused-import warnings if any reference is dropped above.
    @SuppressWarnings("unused")
    private static void keepImports(TxOutputKey k, TxOutput v, SpentInfo s, Issuance i) {}
}

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

package bisq.core.dao.state.model;

import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.ParamChange;
import bisq.core.encoding.canonical.LegacyHashMapOrderMapEntryIterator;

import com.google.common.annotations.VisibleForTesting;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serializes the DAO state monitor input with the map entry order produced by the old Java 11 HashMap path.
 * The DAO state itself remains canonicalized as TreeMaps; this is only for compatibility with legacy seed hashes.
 */
public final class LegacyDaoStateHashSerializerV1 {
    private LegacyDaoStateHashSerializerV1() {
    }

    public static byte[] serialize(DaoState daoState) {
        return getBsqStateBuilderExcludingBlocks(daoState)
                .addBlocks(daoState.getLastBlock().toProtoMessage())
                .build()
                .toByteArray();
    }

    @VisibleForTesting
    static protobuf.DaoState.Builder getBsqStateBuilderExcludingBlocks(DaoState daoState) {
        return protobuf.DaoState.newBuilder()
                .setChainHeight(daoState.getChainHeight())
                .addAllCycles(daoState.getCycles().stream()
                        .map(Cycle::toProtoMessage)
                        .collect(Collectors.toList()))
                .putAllUnspentTxOutputMap(toLegacyHashMapIterationOrder(daoState.getUnspentTxOutputMap(),
                        e -> e.getKey().toString(),
                        e -> e.getValue().toProtoMessage()))
                .putAllSpentInfoMap(toLegacyHashMapIterationOrder(daoState.getSpentInfoMap(),
                        e -> e.getKey().toString(),
                        e -> e.getValue().toProtoMessage()))
                .addAllConfiscatedLockupTxList(daoState.getConfiscatedLockupTxList())
                .putAllIssuanceMap(toLegacyHashMapIterationOrder(daoState.getIssuanceMap(),
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
    }

    private static <K, V, P> LinkedHashMap<String, P> toLegacyHashMapIterationOrder(
            Map<K, V> source,
            Function<Map.Entry<K, V>, String> keyMapper,
            Function<Map.Entry<K, V>, P> valueMapper) {
        List<Map.Entry<String, P>> entries = source.entrySet().stream()
                .map(entry -> new AbstractMap.SimpleImmutableEntry<>(keyMapper.apply(entry), valueMapper.apply(entry)))
                .collect(Collectors.toList());
        return toJava11HashMapIterationOrder(entries);
    }

    @VisibleForTesting
    static List<String> getJava11HashMapIterationOrder(Collection<String> keys) {
        return LegacyHashMapOrderMapEntryIterator.getJava11HashMapIterationOrder(keys);
    }

    @VisibleForTesting
    static <V> LinkedHashMap<String, V> toJava11HashMapIterationOrder(List<Map.Entry<String, V>> entries) {
        return LegacyHashMapOrderMapEntryIterator.toJava11HashMapIterationOrder(entries);
    }
}

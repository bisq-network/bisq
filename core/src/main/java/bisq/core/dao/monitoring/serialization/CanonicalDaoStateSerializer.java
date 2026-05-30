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
import bisq.core.dao.state.model.governance.Issuance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Canonical (post-activation) byte format for the DAO state hash chain.
 *
 * <p><strong>Design intent.</strong> The legacy format
 * ({@link LegacyProtobufDaoStateSerializer}) built a protobuf {@code DaoState}
 * message whose {@code map<...>} fields were populated from a Java
 * {@code HashMap} produced by {@code Collectors.toMap}. That made the hash
 * bytes depend on {@code java.util.HashMap}'s bucket layout, which is brittle
 * across JDK versions. This canonical format removes all protobuf map fields
 * from the hash path and replaces them with explicit sorted-key iteration over
 * the source {@code TreeMap}s. Sorted-key iteration is the sole source of
 * determinism for map fields; no {@code HashMap} appears anywhere on the
 * hash path.
 *
 * <p><strong>Wire format.</strong> Big-endian throughout. All length prefixes
 * are unsigned varints (1..5 bytes). The envelope structure is:
 *
 * <pre>
 *   u8   version_tag          (0x02 for this format)
 *   i32  chain_height         (big-endian, 4 bytes)
 *   list cycles               (per-leaf protobuf bytes, length-prefixed)
 *   map  unspent_tx_output    (TxOutputKey.toString() sorted)
 *   map  spent_info           (TxOutputKey.toString() sorted)
 *   list confiscated_lockup_tx_list   (UTF-8 strings, length-prefixed)
 *   map  issuance             (txId sorted)
 *   list param_change_list
 *   list evaluated_proposal_list
 *   list decrypted_ballots_with_merits_list
 *   block last_block          (length-prefixed protobuf bytes)
 * </pre>
 *
 * <p><strong>Why protobuf bytes for leaves are still safe.</strong> The leaf
 * proto messages reachable from {@code DaoState} ({@code Block}, {@code Tx},
 * {@code TxOutput}, {@code Cycle}, {@code Issuance}, {@code Proposal},
 * {@code EvaluatedProposal}, {@code DecryptedBallotsWithMerits}, etc.)
 * contain map fields only in {@code Proposal.extra_data} and
 * {@code BlindVote.extra_data}. Both Java-side fields are now backed by
 * {@code java.util.TreeMap} (see {@code Proposal#extraDataMap} and
 * {@code BlindVote#extraDataMap}), so when protobuf's
 * {@code LinkedHashMap}-backed {@code MapFieldLite} is populated from those
 * TreeMaps it preserves the sorted insertion order. The resulting leaf bytes
 * are therefore JDK-independent.
 *
 * <p><strong>Map iteration.</strong> Map entries are written in the natural
 * sort order of the source {@code TreeMap}: for the unspent/spent maps the
 * key is {@code TxOutputKey.toString()} (matching the legacy wire-key format),
 * for the issuance map the key is the {@code txId} string. No tie-breaking is
 * needed because keys are unique.
 *
 * <p><strong>Versioning.</strong> The leading {@code version_tag} byte allows
 * future format changes to be activated at a new block height without
 * ambiguity. Legacy bytes have no version tag and are produced by
 * {@link LegacyProtobufDaoStateSerializer}.
 */
public class CanonicalDaoStateSerializer implements DaoStateHashSerializer {

    static final byte VERSION_TAG = 0x02;

    @Override
    public byte[] serialize(DaoState daoState) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            out.write(VERSION_TAG);
            writeInt32(out, daoState.getChainHeight());

            writeProtoList(out, daoState.getCycles(), c -> c.toProtoMessage().toByteArray());

            writeSortedMap(out, daoState.getUnspentTxOutputMap(),
                    e -> bytes(e.getKey().toString()),
                    e -> e.getValue().toProtoMessage().toByteArray());

            writeSortedMap(out, daoState.getSpentInfoMap(),
                    e -> bytes(e.getKey().toString()),
                    e -> e.getValue().toProtoMessage().toByteArray());

            writeStringList(out, daoState.getConfiscatedLockupTxList());

            writeSortedMap(out, daoState.getIssuanceMap(),
                    e -> bytes(e.getKey()),
                    e -> e.getValue().toProtoMessage().toByteArray());

            writeProtoList(out, daoState.getParamChangeList(), p -> p.toProtoMessage().toByteArray());
            writeProtoList(out, daoState.getEvaluatedProposalList(), p -> p.toProtoMessage().toByteArray());
            writeProtoList(out, daoState.getDecryptedBallotsWithMeritsList(), b -> b.toProtoMessage().toByteArray());

            // Last block as length-prefixed protobuf bytes.
            byte[] lastBlock = daoState.getLastBlock().toProtoMessage().toByteArray();
            writeBytes(out, lastBlock);

            return out.toByteArray();
        } catch (IOException ex) {
            // ByteArrayOutputStream never throws; treat as a programming error.
            throw new IllegalStateException("Failed to serialize DaoState", ex);
        }
    }

    // ----- helpers -----

    @FunctionalInterface
    private interface ProtoMapper<T> {
        byte[] apply(T value);
    }

    @FunctionalInterface
    private interface EntryMapper<K, V> {
        byte[] apply(Map.Entry<K, V> entry);
    }

    private static <T> void writeProtoList(ByteArrayOutputStream out,
                                           Iterable<T> items,
                                           ProtoMapper<T> mapper) throws IOException {
        // Materialize once so we know the count without consuming the iterable twice.
        java.util.ArrayList<byte[]> serialized = new java.util.ArrayList<>();
        for (T item : items) {
            serialized.add(mapper.apply(item));
        }
        writeVarint(out, serialized.size());
        for (byte[] b : serialized) {
            writeBytes(out, b);
        }
    }

    private static <K, V> void writeSortedMap(ByteArrayOutputStream out,
                                              java.util.TreeMap<K, V> source,
                                              EntryMapper<K, V> keyBytes,
                                              EntryMapper<K, V> valueBytes) throws IOException {
        writeVarint(out, source.size());
        for (Map.Entry<K, V> entry : source.entrySet()) {
            writeBytes(out, keyBytes.apply(entry));
            writeBytes(out, valueBytes.apply(entry));
        }
    }

    private static void writeStringList(ByteArrayOutputStream out, java.util.List<String> strings) throws IOException {
        writeVarint(out, strings.size());
        for (String s : strings) {
            writeBytes(out, bytes(s));
        }
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] b) throws IOException {
        writeVarint(out, b.length);
        out.write(b);
    }

    private static void writeInt32(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        // Unsigned LEB128. Caller never passes negative values.
        if (value < 0) {
            throw new IllegalArgumentException("Negative varint: " + value);
        }
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unused")
    private static void keepImports(TxOutputKey k, TxOutput v, SpentInfo s, Issuance i) {}
}

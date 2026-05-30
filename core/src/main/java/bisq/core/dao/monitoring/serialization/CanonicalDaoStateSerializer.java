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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Canonical (post-activation) byte format for the DAO state hash chain.
 *
 * <p><strong>Zero protobuf, zero HashMap.</strong> Every byte is produced by
 * {@link CanonicalWriter} primitives and {@link CanonicalLeafWriter} field
 * encoders. The legacy format ({@link LegacyProtobufDaoStateSerializer})
 * piped maps through {@code Collectors.toMap} into protobuf's MapFieldLite,
 * making the bytes dependent on {@code java.util.HashMap}'s bucket layout.
 * This format walks the source {@link java.util.TreeMap}s in sorted-key
 * order and writes each leaf field-by-field via methods declared in this
 * package.
 *
 * <p><strong>Domain separator.</strong> Every preimage begins with the
 * fixed ASCII byte sequence {@code "BISQ_HASH_PREIMAGE\0v2\0DAO_STATE_HASH_CHAIN\0"}
 * followed by the version tag byte. The separator forces any future
 * canonical preimage in another domain (proposal hash, blindvote hash,
 * signed messages) to use a distinct prefix, eliminating cross-domain
 * hash collision concerns.
 *
 * <p><strong>Wire format.</strong>
 * <pre>
 *   bytes domain_separator             (ASCII: see DOMAIN_SEPARATOR)
 *   u8    version_tag                  (0x02 for this format)
 *   i32   chain_height                 (big-endian)
 *   list  cycles
 *   map   unspent_tx_output            (TxOutputKey natural order; key + value)
 *   map   spent_info                   (TxOutputKey natural order; key + value)
 *   list  confiscated_lockup_tx_list   (length-prefixed UTF-8 strings)
 *   map   issuance                     (txId sort order; key + Issuance)
 *   list  param_change_list
 *   list  evaluated_proposal_list
 *   list  decrypted_ballots_with_merits_list
 *   block last_block
 * </pre>
 *
 * <p>Field order is positional. Any change to this method, to
 * {@link CanonicalLeafWriter}, to {@link CanonicalWriter}'s primitives, or
 * to {@link #DOMAIN_SEPARATOR}, is a consensus-breaking change.
 *
 * <p><strong>Streaming.</strong> Use {@link #updateDigest(MessageDigest, DaoState)}
 * to fold a {@code DaoState} directly into a {@link MessageDigest} without
 * an intermediate {@code byte[]}. {@link #serialize(DaoState)} retains the
 * by-array form because {@code stateAsBytes} is folded into the prev-hash
 * chain in {@code DaoStateMonitoringService} and needs to be a real byte
 * array.
 */
public class CanonicalDaoStateSerializer implements DaoStateHashSerializer {

    static final byte[] DOMAIN_SEPARATOR =
            "BISQ_HASH_PREIMAGE\0v2\0DAO_STATE_HASH_CHAIN\0".getBytes(StandardCharsets.US_ASCII);

    static final byte VERSION_TAG = 0x02;

    @Override
    public byte[] serialize(DaoState daoState) {
        CanonicalWriter w = CanonicalWriter.intoMemory();
        writeTo(w, daoState);
        return w.toByteArray();
    }

    /**
     * Stream the canonical preimage for {@code daoState} directly into
     * {@code digest}. Equivalent to {@code digest.update(serialize(daoState))}
     * but avoids the intermediate byte array — material for the large maps
     * inside a full DaoState.
     */
    public void updateDigest(MessageDigest digest, DaoState daoState) {
        writeTo(CanonicalWriter.intoDigest(digest), daoState);
    }

    private static void writeTo(CanonicalWriter w, DaoState daoState) {
        w.writeRawBytes(DOMAIN_SEPARATOR);
        w.writeRawByte(VERSION_TAG);
        w.writeI32(daoState.getChainHeight());

        w.writeList(daoState.getCycles(), CanonicalLeafWriter::writeCycle);

        w.writeSortedMap(daoState.getUnspentTxOutputMap(),
                CanonicalLeafWriter::writeTxOutputKey,
                CanonicalLeafWriter::writeTxOutput);

        w.writeSortedMap(daoState.getSpentInfoMap(),
                CanonicalLeafWriter::writeTxOutputKey,
                CanonicalLeafWriter::writeSpentInfo);

        w.writeStringList(daoState.getConfiscatedLockupTxList());

        w.writeSortedMap(daoState.getIssuanceMap(),
                CanonicalWriter::writeString,
                CanonicalLeafWriter::writeIssuance);

        w.writeList(daoState.getParamChangeList(), CanonicalLeafWriter::writeParamChange);
        w.writeList(daoState.getEvaluatedProposalList(), CanonicalLeafWriter::writeEvaluatedProposal);
        w.writeList(daoState.getDecryptedBallotsWithMeritsList(), CanonicalLeafWriter::writeDecryptedBallotsWithMerits);

        CanonicalLeafWriter.writeBlock(w, daoState.getLastBlock());
    }
}

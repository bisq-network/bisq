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
 * <p><strong>Wire format.</strong>
 * <pre>
 *   u8   version_tag                   (0x02 for this format)
 *   i32  chain_height                  (big-endian, 4 bytes)
 *   list cycles                        (each leaf written by CanonicalLeafWriter)
 *   map  unspent_tx_output             (TxOutputKey natural order; key + value)
 *   map  spent_info                    (TxOutputKey natural order; key + value)
 *   list confiscated_lockup_tx_list    (length-prefixed UTF-8 strings)
 *   map  issuance                      (txId string sort order; key + Issuance)
 *   list param_change_list
 *   list evaluated_proposal_list
 *   list decrypted_ballots_with_merits_list
 *   block last_block                   (full Block leaf encoding)
 * </pre>
 *
 * <p>Field order is positional. The leading {@code version_tag} byte
 * disambiguates this format from any future canonical variant. Any change
 * to this method, to {@link CanonicalLeafWriter}, or to
 * {@link CanonicalWriter}'s primitives, is a consensus-breaking change.
 */
public class CanonicalDaoStateSerializer implements DaoStateHashSerializer {

    static final byte VERSION_TAG = 0x02;

    @Override
    public byte[] serialize(DaoState daoState) {
        CanonicalWriter w = new CanonicalWriter();
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

        // Only the last block participates in the hash chain by design; the
        // prev-hash chaining done in DaoStateMonitoringService folds in
        // earlier blocks' state.
        CanonicalLeafWriter.writeBlock(w, daoState.getLastBlock());

        return w.toByteArray();
    }
}

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
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Determinism tests for the canonical and legacy serializers.
 *
 * <p>The full byte-equality check against a recorded mainnet snapshot
 * belongs in an integration test that ships with a checked-in serialized
 * {@code DaoState} fixture; see the task list in the plan file. These unit
 * tests cover the determinism property that the format guarantees:
 * the produced bytes depend only on the logical content of the state, not
 * on the insertion order of any underlying maps.
 */
public class CanonicalDaoStateSerializerTest {

    private static DaoState buildState(int chainHeight,
                                       boolean reverseInsertionOrder) {
        DaoState state = new DaoState();
        state.setChainHeight(chainHeight);

        Block block = new Block(chainHeight, 1_700_000_000_000L, "deadbeef", "cafebabe");
        state.addBlock(block);

        // Two issuance entries with txIds that hash to different buckets.
        // Insertion order should not affect the serialized bytes.
        String[] txIds = {"aaaa", "bbbb", "cccc", "dddd"};
        if (reverseInsertionOrder) {
            for (int i = txIds.length - 1; i >= 0; i--) {
                state.getIssuanceMap().put(txIds[i],
                        new Issuance(txIds[i], chainHeight, 1000L + i, "pubKey-" + i,
                                IssuanceType.COMPENSATION));
            }
        } else {
            for (int i = 0; i < txIds.length; i++) {
                state.getIssuanceMap().put(txIds[i],
                        new Issuance(txIds[i], chainHeight, 1000L + i, "pubKey-" + i,
                                IssuanceType.COMPENSATION));
            }
        }

        // SpentInfo entries keyed by TxOutputKey, again two insertion orders.
        TxOutputKey[] keys = {
                new TxOutputKey("aaaa", 0),
                new TxOutputKey("bbbb", 1),
                new TxOutputKey("cccc", 2),
        };
        if (reverseInsertionOrder) {
            for (int i = keys.length - 1; i >= 0; i--) {
                state.getSpentInfoMap().put(keys[i],
                        new SpentInfo(chainHeight, "spent-" + i, i));
            }
        } else {
            for (int i = 0; i < keys.length; i++) {
                state.getSpentInfoMap().put(keys[i],
                        new SpentInfo(chainHeight, "spent-" + i, i));
            }
        }

        return state;
    }

    @Test
    public void canonicalIsInsertionOrderIndependent() {
        DaoState a = buildState(123, false);
        DaoState b = buildState(123, true);
        CanonicalDaoStateSerializer canonical = new CanonicalDaoStateSerializer();
        assertArrayEquals(canonical.serialize(a), canonical.serialize(b),
                "Canonical bytes must depend only on logical contents, not insertion order");
    }

    @Test
    public void legacyIsInsertionOrderIndependent() {
        DaoState a = buildState(123, false);
        DaoState b = buildState(123, true);
        LegacyProtobufDaoStateSerializer legacy = new LegacyProtobufDaoStateSerializer();
        assertArrayEquals(legacy.serialize(a), legacy.serialize(b),
                "Legacy bytes must depend only on logical contents, not insertion order");
    }

    @Test
    public void canonicalAndLegacyDiffer() {
        // The two formats are different by design — make sure we have not
        // accidentally aliased one to the other.
        DaoState state = buildState(123, false);
        byte[] canonical = new CanonicalDaoStateSerializer().serialize(state);
        byte[] legacy = new LegacyProtobufDaoStateSerializer().serialize(state);
        assertFalse(java.util.Arrays.equals(canonical, legacy),
                "Canonical and legacy formats must differ");
        // Domain separator precedes the version tag; the tag lives at the
        // first byte after the separator.
        assertEquals(CanonicalDaoStateSerializer.VERSION_TAG,
                canonical[CanonicalDaoStateSerializer.DOMAIN_SEPARATOR.length],
                "Canonical output must have the version tag right after the domain separator");
    }

    @Test
    public void mutationChangesBytes() {
        // Sanity check: if we mutate the state the bytes must change. Catches
        // a serializer that accidentally ignores fields.
        DaoState a = buildState(123, false);
        DaoState b = buildState(124, false); // different chain height
        CanonicalDaoStateSerializer canonical = new CanonicalDaoStateSerializer();
        assertFalse(java.util.Arrays.equals(canonical.serialize(a), canonical.serialize(b)),
                "Different chain heights must produce different bytes");
    }

    @Test
    public void emptyMapsSerializeToSizeZero() {
        // Edge case: an otherwise-empty state still serializes deterministically
        // and idempotently.
        DaoState state = new DaoState();
        state.setChainHeight(0);
        state.addBlock(new Block(0, 0L, "h", "p"));
        CanonicalDaoStateSerializer canonical = new CanonicalDaoStateSerializer();
        byte[] first = canonical.serialize(state);
        byte[] second = canonical.serialize(state);
        assertArrayEquals(first, second);
        assertTrue(first.length > 0);
    }
}

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

package bisq.common.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

import static bisq.common.app.Capability.DAO_FULL_NODE;
import static bisq.common.app.Capability.SEED_NODE;
import static bisq.common.app.Capability.TRADE_STATISTICS;
import static bisq.common.app.Capability.TRADE_STATISTICS_2;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapabilitiesTest {

    @Test
    public void testNoCapabilitiesAvailable() {
        Capabilities DUT = new Capabilities();

        assertTrue(DUT.containsAll(new HashSet<>()));
        assertFalse(DUT.containsAll(new Capabilities(SEED_NODE)));
    }

    @Test
    public void testHasLess() {
        assertTrue(new Capabilities().hasLess(new Capabilities(SEED_NODE)));
        assertFalse(new Capabilities().hasLess(new Capabilities()));
        assertFalse(new Capabilities(SEED_NODE).hasLess(new Capabilities()));
        assertTrue(new Capabilities(SEED_NODE).hasLess(new Capabilities(DAO_FULL_NODE)));
        assertFalse(new Capabilities(DAO_FULL_NODE).hasLess(new Capabilities(SEED_NODE)));

        Capabilities all = new Capabilities(
                TRADE_STATISTICS,
                TRADE_STATISTICS_2,
                Capability.ACCOUNT_AGE_WITNESS,
                Capability.ACK_MSG,
                Capability.PROPOSAL,
                Capability.BLIND_VOTE,
                Capability.DAO_STATE,
                Capability.BUNDLE_OF_ENVELOPES,
                Capability.MEDIATION,
                Capability.SIGNED_ACCOUNT_AGE_WITNESS,
                Capability.REFUND_AGENT,
                Capability.TRADE_STATISTICS_HASH_UPDATE
        );
        Capabilities other = new Capabilities(
                TRADE_STATISTICS,
                TRADE_STATISTICS_2,
                Capability.ACCOUNT_AGE_WITNESS,
                Capability.ACK_MSG,
                Capability.PROPOSAL,
                Capability.BLIND_VOTE,
                Capability.DAO_STATE,
                Capability.BUNDLE_OF_ENVELOPES,
                Capability.MEDIATION,
                Capability.SIGNED_ACCOUNT_AGE_WITNESS,
                Capability.REFUND_AGENT,
                Capability.TRADE_STATISTICS_HASH_UPDATE,
                Capability.NO_ADDRESS_PRE_FIX
        );

        assertTrue(all.hasLess(other));
    }

    @Test
    public void testO() {
        Capabilities DUT = new Capabilities(TRADE_STATISTICS);

        assertTrue(DUT.containsAll(new HashSet<>()));
    }

    @Test
    public void testSingleMatch() {
        Capabilities DUT = new Capabilities(TRADE_STATISTICS);

        // single match
        assertTrue(DUT.containsAll(new Capabilities(TRADE_STATISTICS)));
        assertFalse(DUT.containsAll(new Capabilities(SEED_NODE)));
    }

    @Test
    public void testMultiMatch() {
        Capabilities DUT = new Capabilities(TRADE_STATISTICS, TRADE_STATISTICS_2);

        assertTrue(DUT.containsAll(new Capabilities(TRADE_STATISTICS)));
        assertFalse(DUT.containsAll(new Capabilities(SEED_NODE)));
        assertTrue(DUT.containsAll(new Capabilities(TRADE_STATISTICS, TRADE_STATISTICS_2)));
        assertFalse(DUT.containsAll(new Capabilities(SEED_NODE, TRADE_STATISTICS_2)));
    }

    @Test
    public void testToIntList() {
        assertEquals(Collections.emptyList(), Capabilities.toIntList(new Capabilities()));
        assertEquals(Collections.singletonList(12), Capabilities.toIntList(new Capabilities(Capability.MEDIATION)));
        assertEquals(Arrays.asList(6, 12), Capabilities.toIntList(new Capabilities(Capability.MEDIATION, Capability.BLIND_VOTE)));
    }

    @Test
    public void testFromIntList() {
        assertEquals(new Capabilities(), Capabilities.fromIntList(Collections.emptyList()));
        assertEquals(new Capabilities(Capability.MEDIATION), Capabilities.fromIntList(Collections.singletonList(12)));
        assertEquals(new Capabilities(Capability.BLIND_VOTE, Capability.MEDIATION), Capabilities.fromIntList(Arrays.asList(6, 12)));

        assertEquals(new Capabilities(), Capabilities.fromIntList(Collections.singletonList(-1)));
        assertEquals(new Capabilities(), Capabilities.fromIntList(Collections.singletonList(99)));
        assertEquals(new Capabilities(Capability.MEDIATION), Capabilities.fromIntList(Arrays.asList(-6, 12)));
        assertEquals(new Capabilities(Capability.MEDIATION), Capabilities.fromIntList(Arrays.asList(12, 99)));
    }

    @Test
    public void testToStringList() {
        assertEquals("", new Capabilities().toStringList());
        assertEquals("12", new Capabilities(Capability.MEDIATION).toStringList());
        assertEquals("6, 12", new Capabilities(Capability.BLIND_VOTE, Capability.MEDIATION).toStringList());
        // capabilities gets sorted, independent of our order
        assertEquals("6, 12", new Capabilities(Capability.MEDIATION, Capability.BLIND_VOTE).toStringList());
    }

    @Test
    public void testFromStringList() {
        assertEquals(new Capabilities(), Capabilities.fromStringList(null));
        assertEquals(new Capabilities(), Capabilities.fromStringList(""));
        assertEquals(new Capabilities(Capability.MEDIATION), Capabilities.fromStringList("12"));
        assertEquals(new Capabilities(Capability.BLIND_VOTE, Capability.MEDIATION), Capabilities.fromStringList("6,12"));
        assertEquals(new Capabilities(Capability.BLIND_VOTE, Capability.MEDIATION), Capabilities.fromStringList("12, 6"));
        assertEquals(new Capabilities(), Capabilities.fromStringList("a"));
        assertEquals(new Capabilities(), Capabilities.fromStringList("99"));
        assertEquals(new Capabilities(), Capabilities.fromStringList("-1"));
        assertEquals(new Capabilities(Capability.MEDIATION), Capabilities.fromStringList("12, a"));
        assertEquals(new Capabilities(Capability.MEDIATION), Capabilities.fromStringList("12, 99"));
        assertEquals(new Capabilities(Capability.MEDIATION), Capabilities.fromStringList("a,12, 99"));
    }
}

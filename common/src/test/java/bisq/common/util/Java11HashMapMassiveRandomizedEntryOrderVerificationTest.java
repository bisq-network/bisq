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

package bisq.common.util;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Java11HashMapMassiveRandomizedEntryOrderVerificationTest {
    @Test
    public void java11HashMapMatchesPinnedMassiveRandomizedEntryOrder() {
        Assumptions.assumeTrue(Runtime.version().feature() == 11,
                "This verifies the checked-in massive entry-order output against real java.util.HashMap on Java 11 only.");

        Map<String, Integer> map = new HashMap<>();
        LegacyHashMapMassiveRandomizedEntryOrderSupport.fillMassiveRandomizedMap(map);

        assertEquals(LegacyHashMapJava11EntryOrder.ENTRY_COUNT, map.size());
        assertArrayEquals(LegacyHashMapJava11EntryOrder.massiveRandomizedEntryValueOrder(),
                LegacyHashMapMassiveRandomizedEntryOrderSupport.entryValueOrder(map),
                "Pinned massive randomized entry order must match Java 11 HashMap");
    }
}

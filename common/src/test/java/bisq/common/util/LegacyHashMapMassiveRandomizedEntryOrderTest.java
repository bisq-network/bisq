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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LegacyHashMapMassiveRandomizedEntryOrderTest {
    @Test
    public void legacyHashMapMatchesPinnedJava11MassiveRandomizedEntryOrder() {
        Map<String, Integer> map = new LegacyHashMap<>();
        LegacyHashMapMassiveRandomizedEntryOrderSupport.fillMassiveRandomizedMap(map);

        int[] actualOrder = LegacyHashMapMassiveRandomizedEntryOrderSupport.entryValueOrder(map);

        assertEquals(LegacyHashMapJava11EntryOrder.ENTRY_COUNT, map.size());
        assertArrayEquals(LegacyHashMapJava11EntryOrder.massiveRandomizedEntryValueOrder(),
                actualOrder,
                "LegacyHashMap massive randomized entry order must match Java 11 HashMap");
    }

    @Test
    public void outputLegacyHashMapMassiveRandomizedEntryOrder() {
        Map<String, Integer> map = new LegacyHashMap<>();
        LegacyHashMapMassiveRandomizedEntryOrderSupport.fillMassiveRandomizedMap(map);

        int[] actualOrder = LegacyHashMapMassiveRandomizedEntryOrderSupport.entryValueOrder(map);

        System.out.print(LegacyHashMapMassiveRandomizedEntryOrderSupport.entryOrderOutput(map));
        assertArrayEquals(LegacyHashMapJava11EntryOrder.massiveRandomizedEntryValueOrder(),
                actualOrder,
                "Printed LegacyHashMap entry order must match the pinned Java 11 order");
    }
}

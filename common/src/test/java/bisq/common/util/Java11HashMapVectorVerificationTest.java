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

import bisq.common.util.LegacyHashMapJava11VectorSupport.Vector;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Java11HashMapVectorVerificationTest {
    @Test
    public void java11HashMapMatchesCheckedInVectorOrder() {
        Assumptions.assumeTrue(Runtime.version().feature() == 11,
                "This verifies the checked-in vectors against real java.util.HashMap on Java 11 only.");

        for (Vector vector : LegacyHashMapJava11Vectors.VECTORS) {
            Map<String, Integer> map = LegacyHashMapJava11VectorSupport.buildMap(
                    vector,
                    HashMap::new,
                    HashMap::new);

            assertEquals(vector.expectedSize, map.size(), vector.name + " size");
            assertEquals(vector.expectedKeyOrderSha256,
                    LegacyHashMapJava11VectorSupport.keyOrderDigest(map),
                    vector.name + " key order");
        }
    }
}

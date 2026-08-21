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

package bisq.bridge.grpc.services;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static bisq.bridge.grpc.services.BurningmanRetention.includeBlock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class BurningmanRetentionTest {
    @Test
    public void testIncludeBlock() {
        assertThrows(IllegalArgumentException.class, () -> {
            includeBlock(0, 1);
        });

        assertEquals(10, resolve(100, 100));
        // with 100 blocks we have 19 not 20, as one match would be covered by both mod 10 and mod 100
        assertEquals(19, resolve(1000, 1000));
        // with 100 blocks we have 28 not 30, as one match would be covered by mod 10 and mod 100 and mod 1000
        assertEquals(28, resolve(10000, 10000));

        // current block on Jul 25th 2025
        assertEquals(28, resolve(907074, 10000));

        int fromBlock = 907074; // current block on Jul 25th 2025
        int toBlock = 892674; // past 100 days = 907074 - 14400 = 892674
        for (int i = fromBlock; i >= toBlock; i--) {
            assertEquals(28, resolve(i, 10000));
        }
    }

    private static int resolve(int chainHeightTip, int lookBack) {
        int blockHeight = chainHeightTip - lookBack;
        int numIncludes = 0;
        for (int i = chainHeightTip; i > blockHeight; i--) {
            if (includeBlock(chainHeightTip, i)) {
                numIncludes++;
            }
        }
        return numIncludes;
    }

}

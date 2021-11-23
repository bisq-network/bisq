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

package bisq.common.crypto;

import bisq.common.crypto.Equihash.Puzzle.Solution;

import com.google.common.base.Strings;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EquihashTest {
    @Test
    public void testHashUpperBound() {
        assertEquals("ffffffff ffffffff ffffffff ffffffff ffffffff ffffffff ffffffff ffffffff", hub(1));
        assertEquals("aaaaaaaa aaaaaaaa aaaaaaaa aaaaaaaa aaaaaaaa aaaaaaaa aaaaaaaa aaaaaaaa", hub(1.5));
        assertEquals("7fffffff ffffffff ffffffff ffffffff ffffffff ffffffff ffffffff ffffffff", hub(2));
        assertEquals("55555555 55555555 55555555 55555555 55555555 55555555 55555555 55555555", hub(3));
        assertEquals("3fffffff ffffffff ffffffff ffffffff ffffffff ffffffff ffffffff ffffffff", hub(4));
        assertEquals("33333333 33333333 33333333 33333333 33333333 33333333 33333333 33333333", hub(5));
        assertEquals("051eb851 eb851eb8 51eb851e b851eb85 1eb851eb 851eb851 eb851eb8 51eb851e", hub(50.0));
        assertEquals("0083126e 978d4fdf 3b645a1c ac083126 e978d4fd f3b645a1 cac08312 6e978d4f", hub(500.0));
        assertEquals("00000000 00000000 2f394219 248446ba a23d2ec7 29af3d61 0607aa01 67dd94ca", hub(1.0e20));
        assertEquals("00000000 00000000 00000000 00000000 ffffffff ffffffff ffffffff ffffffff", hub(0x1.0p128));
        assertEquals("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000", hub(Double.POSITIVE_INFINITY));
    }

    @Test
    public void testFindSolution() {
        Equihash equihash = new Equihash(90, 5, 5.0);
        byte[] seed = new byte[64];
        Solution solution = equihash.puzzle(seed).findSolution();

        byte[] solutionBytes = solution.serialize();
        Solution roundTrippedSolution = equihash.puzzle(seed).deserializeSolution(solutionBytes);

        assertTrue(solution.verify());
        assertEquals(72, solutionBytes.length);
        assertEquals(solution.toString(), roundTrippedSolution.toString());
    }

    private static String hub(double difficulty) {
        return hexString(Equihash.hashUpperBound(difficulty));
    }

    private static String hexString(int[] ints) {
        return Arrays.stream(ints)
                .mapToObj(n -> Strings.padStart(Integer.toHexString(n), 8, '0'))
                .collect(Collectors.joining(" "));
    }
}

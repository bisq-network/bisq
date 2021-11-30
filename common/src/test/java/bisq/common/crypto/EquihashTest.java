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
import bisq.common.util.Utilities;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import static java.lang.Double.POSITIVE_INFINITY;
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
        assertEquals("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000", hub(POSITIVE_INFINITY));
    }

    @Test
    public void testAdjustDifficulty() {
        assertEquals(1.0, Equihash.adjustDifficulty(0.0), 0.0001);
        assertEquals(1.0, Equihash.adjustDifficulty(0.5), 0.0001);
        assertEquals(1.0, Equihash.adjustDifficulty(1.0), 0.0001);
        assertEquals(1.0, Equihash.adjustDifficulty(1.1), 0.0001);
        assertEquals(1.12, Equihash.adjustDifficulty(1.2), 0.01);
        assertEquals(1.83, Equihash.adjustDifficulty(1.5), 0.01);
        assertEquals(2.89, Equihash.adjustDifficulty(2.0), 0.01);
        assertEquals(3.92, Equihash.adjustDifficulty(2.5), 0.01);
        assertEquals(4.93, Equihash.adjustDifficulty(3.0), 0.01);
        assertEquals(200.0, Equihash.adjustDifficulty(100.0), 1.5);
        assertEquals(Equihash.adjustDifficulty(POSITIVE_INFINITY), POSITIVE_INFINITY, 1.0);
    }

    @Test
    public void testFindSolution() {
        Equihash equihash = new Equihash(90, 5, 2.0);
        byte[] seed = new byte[32];
        Solution solution = equihash.puzzle(seed).findSolution();

        byte[] solutionBytes = solution.serialize();
        Solution roundTrippedSolution = equihash.puzzle(seed).deserializeSolution(solutionBytes);

        assertTrue(solution.verify());
        assertEquals(72, solutionBytes.length);
        assertEquals(solution.toString(), roundTrippedSolution.toString());
    }

    @Test
    @Ignore
    public void benchmarkFindSolution() {
        // On Intel Core i3 CPU M 330 @ 2.13GHz ...
        //
        // For Equihash-90-5 with real difficulty 2.0, adjusted difficulty 2.8853900817779268 ...
        // Total elapsed solution time: 279583 ms
        // Mean time to solve one puzzle: 279 ms
        // Puzzle solution time per unit difficulty: 139 ms
        //
        double adjustedDifficulty = Equihash.adjustDifficulty(2.0);
        Equihash equihash = new Equihash(90, 5, adjustedDifficulty);

        Stopwatch stopwatch = Stopwatch.createStarted();
        for (int i = 0; i < 1000; i++) {
            byte[] seed = Utilities.intsToBytesBE(new int[]{0, 0, 0, 0, 0, 0, 0, i});
            equihash.puzzle(seed).findSolution();
        }
        stopwatch.stop();
        var duration = stopwatch.elapsed();

        System.out.println("For Equihash-90-5 with real difficulty 2.0, adjusted difficulty " + adjustedDifficulty + " ...");
        System.out.println("Total elapsed solution time: " + duration.toMillis() + " ms");
        System.out.println("Mean time to solve one puzzle: " + duration.dividedBy(1000).toMillis() + " ms");
        System.out.println("Puzzle solution time per unit difficulty: " + duration.dividedBy(2000).toMillis() + " ms");
    }

    @Test
    @Ignore
    public void benchmarkVerify() {
        // On Intel Core i3 CPU M 330 @ 2.13GHz ...
        //
        // For Equihash-90-5 ...
        // Total elapsed verification time: 50046 ms
        // Mean time to verify one solution: 50046 ns
        //
        Equihash equihash = new Equihash(90, 5, 1.0);
        byte[] seed = new byte[32];
        Solution solution = equihash.puzzle(seed).findSolution();

        Stopwatch stopwatch = Stopwatch.createStarted();
        for (int i = 0; i < 1_000_000; i++) {
            solution.verify();
        }
        stopwatch.stop();
        var duration = stopwatch.elapsed();

        System.out.println("For Equihash-90-5 ...");
        System.out.println("Total elapsed verification time: " + duration.toMillis() + " ms");
        System.out.println("Mean time to verify one solution: " + duration.dividedBy(1_000_000).toNanos() + " ns");
    }

    private static final int SAMPLE_NO = 10000;

    @Test
    @Ignore
    public void solutionCountPerNonceStats() {
        // For Equihash-60-4...
        // Got puzzle solution count mean: 1.9797
        // Got expected count stats: [0 x 1381, 1 x 2734, 2 x 2707, 3 x 1786, 4 x 884, 5 x 350, 6 x 115, 7 x 33, 8 x 8, 9 x 2]
        // Got actual count stats:   [0 x 1413, 1 x 2704, 2 x 2699, 3 x 1813, 4 x 866, 5 x 330, 6 x 115, 7 x 35, 8 x 19, 9 x 5, 10]
        //
        // For Equihash-70-4...
        // Got puzzle solution count mean: 1.9988
        // Got expected count stats: [0 x 1355, 1 x 2708, 2 x 2707, 3 x 1803, 4 x 902, 5 x 360, 6 x 120, 7 x 34, 8 x 9, 9 x 2]
        // Got actual count stats:   [0 x 1362, 1 x 2690, 2 x 2720, 3 x 1826, 4 x 870, 5 x 353, 6 x 129, 7 x 41, 8 x 7, 9 x 2]
        //
        // For Equihash-90-5...
        // Got puzzle solution count mean: 1.9921
        // Got expected count stats: [0 x 1364, 1 x 2717, 2 x 2707, 3 x 1797, 4 x 896, 5 x 356, 6 x 119, 7 x 33, 8 x 9, 9 x 2]
        // Got actual count stats:   [0 x 1379, 1 x 2709, 2 x 2729, 3 x 1750, 4 x 900, 5 x 362, 6 x 119, 7 x 39, 8 x 11, 9, 10]
        //
        // For Equihash-96-5...
        // Got puzzle solution count mean: 1.9997
        // Got expected count stats: [0 x 1354, 1 x 2707, 2 x 2707, 3 x 1804, 4 x 902, 5 x 360, 6 x 121, 7 x 34, 8 x 9, 9 x 2]
        // Got actual count stats:   [0 x 1405, 1 x 2621, 2 x 2733, 3 x 1802, 4 x 928, 5 x 342, 6 x 123, 7 x 29, 8 x 13, 9 x 3, 10]
        //
        Equihash equihash = new Equihash(90, 5, 1.0);
        byte[] seed = new byte[32];

        Multiset<Integer> stats = ConcurrentHashMultiset.create();
        IntStream.range(0, SAMPLE_NO).parallel().forEach(nonce ->
                stats.add(equihash.puzzle(seed).countAllSolutionsForNonce(nonce)));

        double mean = (stats.entrySet().stream()
                .mapToInt(entry -> entry.getElement() * entry.getCount())
                .sum()) / (double) SAMPLE_NO;

        System.out.println("For Equihash-90-5...");
        System.out.println("Got puzzle solution count mean: " + mean);
        System.out.println("Got expected count stats: " + expectedStatsFromPoissonDistribution(mean));
        System.out.println("Got actual count stats:   " + stats);
    }

    private Multiset<Integer> expectedStatsFromPoissonDistribution(double mean) {
        var setBuilder = ImmutableMultiset.<Integer>builder();
        double prob = Math.exp(-mean), roundError = 0.0;
        for (int i = 0, total = 0; total < SAMPLE_NO; i++) {
            int n = (int) (roundError + prob * SAMPLE_NO + 0.5);
            setBuilder.addCopies(i, n);
            roundError += prob * SAMPLE_NO - n;
            total += n;
            prob *= mean / (i + 1);
        }
        return setBuilder.build();
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

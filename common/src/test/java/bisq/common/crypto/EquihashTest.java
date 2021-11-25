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

import static bisq.common.crypto.Equihash.EQUIHASH_n_5_MEAN_SOLUTION_COUNT_PER_NONCE;
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
        assertEquals(1.0, Equihash.adjustDifficulty(0.0, 1.34), 0.0001);
        assertEquals(1.0, Equihash.adjustDifficulty(0.5, 1.34), 0.0001);
        assertEquals(1.0, Equihash.adjustDifficulty(1.0, 1.34), 0.0001);
        assertEquals(1.0, Equihash.adjustDifficulty(1.2, 1.34), 0.0001);
        assertEquals(1.22, Equihash.adjustDifficulty(1.5, 1.34), 0.01);
        assertEquals(1.93, Equihash.adjustDifficulty(2.0, 1.34), 0.01);
        assertEquals(2.62, Equihash.adjustDifficulty(2.5, 1.34), 0.01);
        assertEquals(3.30, Equihash.adjustDifficulty(3.0, 1.34), 0.01);
        assertEquals(134.0, Equihash.adjustDifficulty(100.0, 1.34), 1.0);
        assertEquals(Equihash.adjustDifficulty(POSITIVE_INFINITY, 1.34), POSITIVE_INFINITY, 1.0);
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
        // For Equihash-90-5 with real difficulty 2.0, adjusted difficulty 1.933211354791211 ...
        // Total elapsed solution time: 292789 ms
        // Mean time to solve one puzzle: 292 ms
        // Puzzle solution time per unit difficulty: 146 ms
        //
        double adjustedDifficulty = Equihash.adjustDifficulty(2.0, EQUIHASH_n_5_MEAN_SOLUTION_COUNT_PER_NONCE);
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
        // Got puzzle solution count mean: 1.6161
        // Got expected count stats: [0 x 1987, 1 x 3210, 2 x 2595, 3 x 1398, 4 x 564, 5 x 183, 6 x 49, 7 x 11, 8 x 3]
        // Got actual count stats:   [0 x 2014, 1 x 3230, 2 x 2546, 3 x 1395, 4 x 543, 5 x 191, 6 x 50, 7 x 24, 8 x 4, 9 x 3]
        //
        // For Equihash-70-4...
        // Got puzzle solution count mean: 1.6473
        // Got expected count stats: [0 x 1926, 1 x 3172, 2 x 2613, 3 x 1434, 4 x 591, 5 x 195, 6 x 53, 7 x 13, 8 x 2, 9]
        // Got actual count stats:   [0 x 1958, 1 x 3172, 2 x 2584, 3 x 1413, 4 x 585, 5 x 204, 6 x 61, 7 x 17, 8 x 5, 9]
        //
        // For Equihash-90-5...
        // Got puzzle solution count mean: 1.3419
        // Got expected count stats: [0 x 2613, 1 x 3508, 2 x 2353, 3 x 1052, 4 x 353, 5 x 95, 6 x 21, 7 x 4, 8]
        // Got actual count stats:   [0 x 2698, 1 x 3446, 2 x 2311, 3 x 1045, 4 x 352, 5 x 104, 6 x 33, 7 x 5, 8 x 3, 9, 10, 12]
        //
        // For Equihash-96-5...
        // Got puzzle solution count mean: 1.3363
        // Got expected count stats: [0 x 2628, 1 x 3512, 2 x 2347, 3 x 1045, 4 x 349, 5 x 93, 6 x 21, 7 x 4, 8]
        // Got actual count stats:   [0 x 2708, 1 x 3409, 2 x 2344, 3 x 1048, 4 x 368, 5 x 94, 6 x 23, 7 x 6]
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

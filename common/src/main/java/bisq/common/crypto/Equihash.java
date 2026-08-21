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

import bisq.common.util.Utilities;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.ImmutableIntArray;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedInts;

import org.bouncycastle.crypto.digests.Blake2bDigest;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import java.math.BigInteger;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lombok.ToString;

import static com.google.common.base.Preconditions.checkArgument;
import static java.math.BigInteger.ONE;

/**
 * An ASIC-resistant Proof-of-Work scheme based on the Generalized Birthday Problem (GBP),
 * as described in <a href="https://eprint.iacr.org/2015/946.pdf">this paper</a> and used
 * in ZCash and some other cryptocurrencies. Like
 * <a href="https://en.wikipedia.org/wiki/Hashcash">Hashcash</a> but unlike many other
 * memory-hard and ASIC resistant PoW schemes, it is <i>asymmetric</i>, meaning that it
 * supports fast verification. This makes it suitable for DoS attack protection.<br><p>
 * <br>
 * The Generalized Birthday Problem is an attempt to find <i>2<sup>k</sup></i>
 * <i>n</i>-bit hashes (out of a list of length <i>N</i>) which XOR to zero. When <i>N</i>
 * equals <i>2<sup>1+n/(k+1)</sup></i>, this has at least a handful of solutions on
 * average, which can be found using Wagner's Algorithm, as described in the paper and
 * implemented here. The rough idea is to split each hash into <i>k+1</i>
 * <i>n/(k+1)</i>-bit blocks and place them into a table to look for collisions on a given
 * block. All (partially) colliding pairs are XORed together and used to build a new table
 * of roughly the same size as the last, before moving on to the next block and repeating
 * the process until a full collision can be found. Keeping track of the tuple of hashes
 * used to form each intermediate XOR result (which doubles in length each iteration)
 * gives the final solution. The table-based approach needed to find solutions to the GBP
 * makes it a memory-hard problem.<br><p>
 * <br>
 * In this implementation and the reference
 * <a href="https://github.com/khovratovich/equihash">C++ implementation</a> included with
 * the paper, the hash function BLAKE2b is used to supply 256 bits, which is shortened and
 * split into <i>k+1</i> 32-bit blocks. The blocks are masked to provide <i>n/(k+1)</i>
 * bits each and <i>n</i> bits in total. This allows working with 32-bit integers
 * throughout, for efficiency.
 */
@SuppressWarnings("UnstableApiUsage")
public class Equihash {
    private static final int HASH_BIT_LENGTH = 256;
    /** Mean solution count per nonce for Equihash puzzles with unit difficulty. */
    private static final double MEAN_SOLUTION_COUNT_PER_NONCE = 2.0;

    private final int k, N;
    private final int tableCapacity;
    private final int inputNum, inputBits;
    private final int[] hashUpperBound;

    public Equihash(int n, int k, double difficulty) {
        checkArgument(k > 0 && k < HASH_BIT_LENGTH / 32,
                "Tree depth k must be a positive integer less than %s.",
                HASH_BIT_LENGTH / 32);
        checkArgument(n > 0 && n < HASH_BIT_LENGTH && n % (k + 1) == 0,
                "Collision bit count n must be a positive multiple of k + 1 and less than %s.",
                HASH_BIT_LENGTH);
        checkArgument(n / (k + 1) < 30,
                "Sub-collision bit count n / (k + 1) must be less than 30, got %s.",
                n / (k + 1));
        this.k = k;
        inputNum = 1 << k;
        inputBits = n / (k + 1) + 1;
        N = 1 << inputBits;
        tableCapacity = (int) (N * 1.1);
        hashUpperBound = hashUpperBound(difficulty);
    }

    @VisibleForTesting
    static int[] hashUpperBound(double difficulty) {
        return Utilities.bytesToIntsBE(Utilities.copyRightAligned(
                inverseDifficultyMinusOne(difficulty).toByteArray(), HASH_BIT_LENGTH / 8
        ));
    }

    private static BigInteger inverseDifficultyMinusOne(double difficulty) {
        checkArgument(difficulty >= 1.0, "Difficulty must be at least 1.");
        int exponent = Math.getExponent(difficulty) - 52;
        var mantissa = BigInteger.valueOf((long) Math.scalb(difficulty, -exponent));
        var inverse = ONE.shiftLeft(HASH_BIT_LENGTH - exponent).add(mantissa).subtract(ONE).divide(mantissa);
        return inverse.subtract(ONE).max(BigInteger.ZERO);
    }

    /** Adjust the provided difficulty to take the variable number of puzzle solutions per
     * nonce into account, so that the expected number of attempts needed to solve a given
     * puzzle equals the reciprocal of the provided difficulty. */
    public static double adjustDifficulty(double realDifficulty) {
        return Math.max(-MEAN_SOLUTION_COUNT_PER_NONCE / Math.log1p(-1.0 / Math.max(realDifficulty, 1.0)), 1.0);
    }

    public Puzzle puzzle(byte[] seed) {
        return new Puzzle(seed);
    }

    public class Puzzle {
        private final byte[] seed;

        private Puzzle(byte[] seed) {
            this.seed = seed;
        }

        @ToString
        public class Solution {
            private final long nonce;
            private final int[] inputs;

            private Solution(long nonce, int... inputs) {
                this.nonce = nonce;
                this.inputs = inputs;
            }

            public boolean verify() {
                return withHashPrefix(seed, nonce).verify(inputs);
            }

            public byte[] serialize() {
                int bitLen = 64 + inputNum * inputBits;
                int byteLen = (bitLen + 7) / 8;

                byte[] paddedBytes = new byte[byteLen + 3 & -4];
                IntBuffer intBuffer = ByteBuffer.wrap(paddedBytes).asIntBuffer();
                intBuffer.put((int) (nonce >> 32)).put((int) nonce);
                int off = 64;
                long buf = 0;

                for (int v : inputs) {
                    off -= inputBits;
                    buf |= UnsignedInts.toLong(v) << off;
                    if (off <= 32) {
                        intBuffer.put((int) (buf >> 32));
                        buf <<= 32;
                        off += 32;
                    }
                }
                if (off < 64) {
                    intBuffer.put((int) (buf >> 32));
                }
                return (byteLen & 3) == 0 ? paddedBytes : Arrays.copyOf(paddedBytes, byteLen);
            }
        }

        public Solution deserializeSolution(byte[] bytes) {
            int bitLen = 64 + inputNum * inputBits;
            int byteLen = (bitLen + 7) / 8;
            checkArgument(bytes.length == byteLen,
                    "Incorrect solution byte length. Expected %s but got %s.",
                    byteLen, bytes.length);
            checkArgument(byteLen == 0 || (byte) (bytes[byteLen - 1] << ((bitLen + 7 & 7) + 1)) == 0,
                    "Nonzero padding bits found at end of solution byte array.");

            byte[] paddedBytes = (byteLen & 3) == 0 ? bytes : Arrays.copyOf(bytes, byteLen + 3 & -4);
            IntBuffer intBuffer = ByteBuffer.wrap(paddedBytes).asIntBuffer();
            long nonce = ((long) intBuffer.get() << 32) | UnsignedInts.toLong(intBuffer.get());
            int[] inputs = new int[inputNum];
            int off = 0;
            long buf = 0;

            for (int i = 0; i < inputs.length; i++) {
                if (off < inputBits) {
                    buf = buf << 32 | UnsignedInts.toLong(intBuffer.get());
                    off += 32;
                }
                off -= inputBits;
                inputs[i] = (int) (buf >>> off) & (N - 1);
            }
            return new Solution(nonce, inputs);
        }

        public Solution findSolution() {
            Optional<int[]> inputs;
            for (int nonce = 0; ; nonce++) {
                if ((inputs = withHashPrefix(seed, nonce).findInputs()).isPresent()) {
                    return new Solution(nonce, inputs.get());
                }
            }
        }

        @VisibleForTesting
        int countAllSolutionsForNonce(long nonce) {
            return (int) withHashPrefix(seed, nonce).streamInputsHits()
                    .map(ImmutableIntArray::copyOf)
                    .distinct()
                    .count();
        }
    }

    private WithHashPrefix withHashPrefix(byte[] seed, long nonce) {
        return new WithHashPrefix(Bytes.concat(seed, Longs.toByteArray(nonce)));
    }

    private class WithHashPrefix {
        private final byte[] prefixBytes;

        private WithHashPrefix(byte[] prefixBytes) {
            this.prefixBytes = prefixBytes;
        }

        private int[] hashInputs(int... inputs) {
            var digest = new Blake2bDigest(HASH_BIT_LENGTH);
            digest.update(prefixBytes, 0, prefixBytes.length);
            byte[] inputBytes = Utilities.intsToBytesBE(inputs);
            digest.update(inputBytes, 0, inputBytes.length);
            byte[] outputBytes = new byte[HASH_BIT_LENGTH / 8];
            digest.doFinal(outputBytes, 0);
            return Utilities.bytesToIntsBE(outputBytes);
        }

        Stream<int[]> streamInputsHits() {
            var table = computeAllHashes();
            for (int i = 0; i < k; i++) {
                table = findCollisions(table, i + 1 < k);
            }
            return IntStream.range(0, table.numRows)
                    .mapToObj(table::getRow)
                    .filter(row -> row.stream().distinct().count() == inputNum)
                    .map(row -> sortInputs(row.toArray()))
                    .filter(this::testDifficultyCondition);
        }

        Optional<int[]> findInputs() {
            return streamInputsHits().findFirst();
        }

        private XorTable computeAllHashes() {
            var tableValues = IntStream.range(0, N).flatMap(i -> {
                int[] hash = hashInputs(i);
                return IntStream.range(0, k + 2).map(j -> j <= k ? hash[j] & (N / 2 - 1) : i);
            });
            return new XorTable(k + 1, 1, ImmutableIntArray.copyOf(tableValues.parallel()));
        }

        private boolean testDifficultyCondition(int[] inputs) {
            int[] difficultyHash = hashInputs(inputs);
            return UnsignedInts.lexicographicalComparator().compare(difficultyHash, hashUpperBound) <= 0;
        }

        boolean verify(int[] inputs) {
            if (inputs.length != inputNum || Arrays.stream(inputs).distinct().count() < inputNum) {
                return false;
            }
            if (Arrays.stream(inputs).anyMatch(i -> i < 0 || i >= N)) {
                return false;
            }
            if (!Arrays.equals(inputs, sortInputs(inputs))) {
                return false;
            }
            if (!testDifficultyCondition(inputs)) {
                return false;
            }
            int[] hashBlockSums = new int[k + 1];
            for (int i = 0; i < inputs.length; i++) {
                int[] hash = hashInputs(inputs[i]);
                for (int j = 0; j <= k; j++) {
                    hashBlockSums[j] ^= hash[j] & (N / 2 - 1);
                }
                for (int ii = i + 1 + inputNum, j = 0; (ii & 1) == 0; ii /= 2, j++) {
                    if (hashBlockSums[j] != 0) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static class XorTable {
        private final int hashWidth, indexTupleWidth, rowWidth, numRows;
        private final ImmutableIntArray values;

        XorTable(int hashWidth, int indexTupleWidth, ImmutableIntArray values) {
            this.hashWidth = hashWidth;
            this.indexTupleWidth = indexTupleWidth;
            this.values = values;
            rowWidth = hashWidth + indexTupleWidth;
            numRows = (values.length() + rowWidth - 1) / rowWidth;
        }

        ImmutableIntArray getRow(int index) {
            return values.subArray(index * rowWidth, index * rowWidth + hashWidth + indexTupleWidth);
        }
    }

    private static class IntListMultimap {
        final int[] shortLists;
        final ListMultimap<Integer, Integer> overspillMultimap;

        IntListMultimap(int keyUpperBound) {
            shortLists = new int[keyUpperBound * 4];
            overspillMultimap = MultimapBuilder.hashKeys().arrayListValues().build();
        }

        PrimitiveIterator.OfInt get(int key) {
            return new PrimitiveIterator.OfInt() {
                int i;
                Iterator<Integer> overspillIterator;

                private Iterator<Integer> overspillIterator() {
                    if (overspillIterator == null) {
                        overspillIterator = overspillMultimap.get(key).iterator();
                    }
                    return overspillIterator;
                }

                @Override
                public int nextInt() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return i < 4 ? ~shortLists[key * 4 + i++] : overspillIterator().next();
                }

                @Override
                public boolean hasNext() {
                    return i < 4 && shortLists[key * 4 + i] < 0 || i == 4 && overspillIterator().hasNext();
                }
            };
        }

        // assumes non-negative values only:
        void put(int key, int value) {
            for (int i = 0; i < 4; i++) {
                if (shortLists[key * 4 + i] == 0) {
                    shortLists[key * 4 + i] = ~value;
                    return;
                }
            }
            overspillMultimap.put(key, value);
        }
    }

    // Apply a single iteration of Wagner's Algorithm.
    private XorTable findCollisions(XorTable table, boolean isPartial) {
        int newHashWidth = isPartial ? table.hashWidth - 1 : 0;
        int newIndexTupleWidth = table.indexTupleWidth * 2;
        int newRowWidth = newHashWidth + newIndexTupleWidth;
        var newTableValues = ImmutableIntArray.builder(
                newRowWidth * (isPartial ? tableCapacity : 10));

        var indexMultimap = new IntListMultimap(N / 2);
        for (int i = 0; i < table.numRows; i++) {
            var row = table.getRow(i);
            var collisionIndices = indexMultimap.get(row.get(0));
            while (collisionIndices.hasNext()) {
                var collidingRow = table.getRow(collisionIndices.nextInt());
                if (isPartial) {
                    for (int j = 1; j < table.hashWidth; j++) {
                        newTableValues.add(collidingRow.get(j) ^ row.get(j));
                    }
                } else if (!collidingRow.subArray(1, table.hashWidth).equals(row.subArray(1, table.hashWidth))) {
                    continue;
                }
                newTableValues.addAll(collidingRow.subArray(table.hashWidth, collidingRow.length()));
                newTableValues.addAll(row.subArray(table.hashWidth, row.length()));
            }
            indexMultimap.put(row.get(0), i);
        }
        return new XorTable(newHashWidth, newIndexTupleWidth, newTableValues.build());
    }

    private static int[] sortInputs(int[] inputs) {
        Deque<int[]> sublistStack = new ArrayDeque<>();
        int[] topSublist;
        for (int input : inputs) {
            topSublist = new int[]{input};
            while (!sublistStack.isEmpty() && sublistStack.peek().length == topSublist.length) {
                topSublist = UnsignedInts.lexicographicalComparator().compare(sublistStack.peek(), topSublist) < 0
                        ? Ints.concat(sublistStack.pop(), topSublist)
                        : Ints.concat(topSublist, sublistStack.pop());
            }
            sublistStack.push(topSublist);
        }
        return sublistStack.pop();
    }
}

package bisq.network.p2p.peers.getdata;

import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.proto.network.NetworkPayload;

import com.google.protobuf.ByteString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.security.SecureRandom;

import java.math.RoundingMode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.IntStream;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public class KeySetDelta implements NetworkPayload {
    @Getter
    private final DeltaParams params;
    private final long[][] tables;
    private transient final int[] sizeExponentSums;

    private KeySetDelta(int... tableSizeExponents) {
        this(new DeltaParams(Long.MIN_VALUE, Long.MAX_VALUE, tableSizeExponents));
    }

    private KeySetDelta(DeltaParams params) {
        this(params, Arrays.stream(params.tableSizeExponents).mapToObj(n -> new long[1 << n]).toArray(long[][]::new));
    }

    private KeySetDelta(DeltaParams params, long[][] tables) {
        this.params = params;
        this.tables = tables;

        sizeExponentSums = params.tableSizeExponents.clone();
        for (int i = 1; i < sizeExponentSums.length; i++) {
            sizeExponentSums[i] += sizeExponentSums[i - 1];
        }
    }

    private int keyIndex(int i, long encodedKey) {
        return (int) (encodedKey >>> 64 - sizeExponentSums[i]) & tables[i].length - 1;
    }

    private OptionalLong readEntry(int i, int j) {
        long key = tables[i][j];
        long encodedKey = key * 1518500249; // Square root of 2 ** 61, rounded down (an arbitrary unstructured odd int)
        return key != 0 && keyIndex(i, encodedKey) == j ? OptionalLong.of(key) : OptionalLong.empty();
    }

    private void xor(long key) {
        long encodedKey = key * 1518500249;
        for (int i = 0; i < tables.length; i++) {
            tables[i][keyIndex(i, encodedKey)] ^= key;
        }
    }

    private void xorFiltered(long key) {
        if (key >= params.lowerBound && key <= params.upperBound) {
            xor(key);
        }
    }

    public KeySetDelta xorAll(KeySetDelta other) {
        for (int i = 0; i < tables.length; i++) {
            for (int j = 0; j < tables[i].length; j++) {
                tables[i][j] ^= other.tables[i][j];
            }
        }
        return this;
    }

    private static Set<Long> xorAll(Set<Long> dst, Set<Long> src) {
        for (long key : src) {
            if (!dst.remove(key)) {
                dst.add(key);
            }
        }
        return dst;
    }

    public Optional<Set<Long>> decode() {
        KeySetDelta delta = new KeySetDelta(params).xorAll(this);
        Set<Long> guesses = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            if (delta.decode(0, guesses)) {
                return Optional.of(guesses);
            }
        }
        return Optional.empty();
    }

    private boolean decode(int i, Set<Long> guesses) {
        if (i >= tables.length) {
            return Arrays.stream(tables).flatMapToLong(Arrays::stream).allMatch(x -> x == 0);
        }
        if (Arrays.stream(tables[i]).noneMatch(x -> x == 0)) {
            // Abort round if current table is completely full - this seems to slightly improve decoding ability.
            return false;
        }

        Set<Long> newGuesses = IntStream.range(0, tables[i].length).boxed()
                .flatMapToLong(j -> readEntry(i, j).stream())
                .collect(HashSet::new, Set::add, Set::addAll);

        newGuesses.forEach(this::xor);
        return decode(i + 1, xorAll(guesses, newGuesses));
    }

    public double estimateUnfilteredDeltaSize() {
        long emptyCount = Arrays.stream(tables[0]).filter(x -> x == 0).count();

        long readableCount = IntStream.range(0, tables[0].length).boxed()
                .flatMapToLong(j -> readEntry(0, j).stream())
                .count();

        double densityEstimate = maximumLikelihoodDensityEstimate(tables[0].length, emptyCount, readableCount);
        if (Double.isInfinite(densityEstimate)) {
            // Make sure we always return a reasonable (finite) delta size estimate.
            densityEstimate = maximumLikelihoodDensityEstimate(tables[0].length, 0, 2);
        }
        double filteredFraction = 0x1.0p-64 + (params.upperBound * 0x1.0p-64 - params.lowerBound * 0x1.0p-64);
        return densityEstimate * tables[0].length / filteredFraction;
    }

    @VisibleForTesting
    static double maximumLikelihoodDensityEstimate(double N, double n, double m) {
        if (n == 0 && m <= 1) {
            return Double.POSITIVE_INFINITY;
        }
        if (n == N) {
            return 0;
        }
        double x = -Math.log(Math.max(n, 1) / N), lastX;
        double dx = 0x1.0p-32;
        do {
            double y = likelihoodDerivative(N, n, m, x);
            double dy = likelihoodDerivative(N, n, m, x + dx) - y;
            lastX = x;
            x = Math.min(Math.max(x - y * dx / dy, x - 1), x + 1);
        } while (!DoubleMath.fuzzyEquals(x, lastX, 0x1.0p-40));
        return x;
    }

    private static double likelihoodDerivative(double N, double n, double m, double x) {
        double p = Math.exp(-x);
        double q = (x * (1 - 1 / N) - 1 / N) * p + 1 / N;
        double y = (1 - 1 / N) * (x - 1) - 1 / N;
        double z = (N - n - m) / (1 - p - q);
        return (n / p - z) + (m / q - z) * y;
    }

    @VisibleForTesting
    public static Collector<Long, ?, KeySetDelta> toKeySetDelta(int... tableSizeExponents) {
        return Collector.of(() -> new KeySetDelta(tableSizeExponents), KeySetDelta::xor, KeySetDelta::xorAll,
                Collector.Characteristics.UNORDERED);
    }

    public static Collector<P2PDataStorage.ByteArray, ?, KeySetDelta> toKeySetDelta(DeltaParams params) {
        ToLongFunction<P2PDataStorage.ByteArray> hashFn = hashFunction(params.salt);
        return Collector.of(
                () -> new KeySetDelta(params),
                (delta, key) -> delta.xorFiltered(hashFn.applyAsLong(key)),
                KeySetDelta::xorAll,
                Collector.Characteristics.UNORDERED
        );
    }

    public static ToLongFunction<P2PDataStorage.ByteArray> hashFunction(byte[] salt) {
        //noinspection UnstableApiUsage
        ByteArrayDataInput dataInput = ByteStreams.newDataInput(salt);
        //noinspection UnstableApiUsage
        HashFunction function = Hashing.sipHash24(dataInput.readLong(), dataInput.readLong());
        return key -> function.hashBytes(key.bytes).asLong();
    }

    @Override
    public protobuf.KeySetDelta toProtoMessage() {
        return protobuf.KeySetDelta.newBuilder()
                .setSalt(ByteString.copyFrom(params.salt))
                .setShortKeyLowerBound(params.lowerBound).setShortKeyUpperBound(params.upperBound)
                .addAllSizeExponents(Ints.asList(params.tableSizeExponents))
                .addAllConcatenatedTables(Longs.asList(Arrays.stream(tables).flatMapToLong(Arrays::stream).toArray()))
                .build();
    }

    public static KeySetDelta fromProto(protobuf.KeySetDelta proto) {
        if (proto.getDefaultInstanceForType().equals(proto)) {
            return null;
        }
        Preconditions.checkArgument(proto.getShortKeyLowerBound() <= proto.getShortKeyUpperBound());
        long[][] tables = new long[proto.getSizeExponentsCount()][];
        for (int i = 0, j = 0; i < tables.length; i++) {
            Preconditions.checkPositionIndex(proto.getSizeExponents(i), 32);
            tables[i] = Longs.toArray(proto.getConcatenatedTablesList().subList(j, j += 1 << proto.getSizeExponents(i)));
        }
        return new KeySetDelta(
                new DeltaParams(
                        proto.getSalt().toByteArray(),
                        proto.getShortKeyLowerBound(),
                        proto.getShortKeyUpperBound(),
                        Ints.toArray(proto.getSizeExponentsList())),
                tables
        );
    }

    @Getter
    @EqualsAndHashCode
    public static class DeltaParams {
        private final byte[] salt;
        private final long lowerBound, upperBound;
        private transient final int[] tableSizeExponents;

        public DeltaParams(long capacity) {
            this(Long.MIN_VALUE, SizeExponents.forCapacity(capacity).upperBound(capacity),
                    SizeExponents.forCapacity(capacity).tableSizeExponents);
        }

        DeltaParams(long lowerBound, long upperBound, int... tableSizeExponents) {
            this(new byte[16], lowerBound, upperBound, tableSizeExponents);
            new SecureRandom().nextBytes(salt);
        }

        DeltaParams(byte[] salt, long lowerBound, long upperBound, int... tableSizeExponents) {
            this.salt = salt;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.tableSizeExponents = tableSizeExponents;
        }
    }

    @Getter
    @VisibleForTesting
    enum SizeExponents {
        TINY(550, 9, 8, 7, 6, 5, 4, 4, 4, 4, 4, 3, 3, 3),     //   8.6 KiB
        SMALL(1200, 10, 9, 8, 7, 6, 5, 4, 4, 3, 3, 3, 2),     //  16.2 KiB
        LARGE(2500, 11, 10, 9, 8, 7, 6, 5, 4, 4),             //  32.0 KiB
        LARGER(5150, 12, 11, 10, 9, 8, 7, 7),                 //  64.0 KiB
        LARGEST(10450, 13, 12, 11, 10, 9, 9);                 // 128.0 KiB

        private final int maxUnfilteredCapacity;
        private final int[] tableSizeExponents;

        SizeExponents(int maxUnfilteredCapacity, int... tableSizeExponents) {
            Preconditions.checkArgument(Arrays.stream(tableSizeExponents).sum() == 64);
            this.maxUnfilteredCapacity = maxUnfilteredCapacity;
            this.tableSizeExponents = tableSizeExponents;
        }

        long upperBound(long capacity) {
            if (capacity <= maxUnfilteredCapacity) {
                return Long.MAX_VALUE;
            }
            double filteredFraction = (double) maxUnfilteredCapacity / capacity;
            return DoubleMath.roundToLong((filteredFraction - 0.5) * 0x1.0p64, RoundingMode.DOWN) - 1;
        }

        static SizeExponents forCapacity(long capacity) {
            return Arrays.stream(values())
                    .filter(s -> capacity <= s.maxUnfilteredCapacity)
                    .findFirst()
                    .orElse(LARGEST);
        }
    }
}

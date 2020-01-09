package bisq.network.p2p.peers.getdata;

import bisq.network.p2p.peers.getdata.KeySetDelta.SizeExponents;
import bisq.network.p2p.storage.P2PDataStorage;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.security.SecureRandom;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class KeySetDeltaTest {
    private final Random rnd = new SecureRandom();

    @Test
    @Ignore
    public void testDecode() {
        ConcurrentSkipListSet<Integer> decodeLimits = new ConcurrentSkipListSet<>();
        AtomicInteger i0 = new AtomicInteger(12000);
        AtomicInteger j0 = new AtomicInteger();
        IntStream.range(0, 10000000).parallel().forEach(k -> {
            int i00 = i0.get();
            List<Long> testKeys = longs(i0.get()).boxed().collect(Collectors.toList());
            KeySetDelta delta0 = testKeys.stream()
                    .collect(KeySetDelta.toKeySetDelta(SizeExponents.SMALL.getTableSizeExponents()));
            int j = j0.getAndIncrement();
            if (j % 1000 == 999) {
                System.out.println("Made " + (j + 1) + " attempts.");
            }
            Optional<Set<Long>> decoded;
            if ((decoded = delta0.decode()).isPresent()) {
                Assert.assertEquals(new HashSet<>(testKeys), decoded.get());
                return;
            }
            for (int i = 500; i <= i00; i++) {
                KeySetDelta delta = testKeys.subList(0, i).stream()
                        .collect(KeySetDelta.toKeySetDelta(SizeExponents.SMALL.getTableSizeExponents()));

                if (!(decoded = delta.decode()).isPresent()) {
                    System.out.println("Failed to decode at " + i + " entries.");
                    decodeLimits.add(i0.accumulateAndGet(i - 1, Math::min));
                    break;
                }
                Assert.assertEquals(new HashSet<>(testKeys.subList(0, i)), decoded.get());
            }
        });
        System.out.println(decodeLimits);
        System.out.println("Summary: " + decodeLimits.stream().mapToDouble(n -> n).summaryStatistics());
    }

    @Test
    public void testToKeySetDelta() {
        Map<P2PDataStorage.ByteArray, ?> testMap = Stream.generate(() -> randomBytes(20))
                .limit(100000)
                .collect(Collectors.toMap(P2PDataStorage.ByteArray::new, bs -> new Object()));

        Set<P2PDataStorage.ByteArray> set = testMap.keySet();
        byte[] salt = randomBytes(16);
        KeySetDelta delta = null;
        for (int i = 0; i < 100; i++) {
            delta = set.parallelStream()
                    .collect(KeySetDelta.toKeySetDelta(new KeySetDelta.DeltaParams(
                            salt, Long.MIN_VALUE, Long.MAX_VALUE, SizeExponents.LARGER.getTableSizeExponents())));
        }
        System.out.println(delta.estimateUnfilteredDeltaSize());
    }

    @Test
    public void testProto() {
        KeySetDelta delta = Stream.generate(() -> randomBytes(20))
                .limit(1200)
                .map(P2PDataStorage.ByteArray::new)
                .collect(KeySetDelta.toKeySetDelta(new KeySetDelta.DeltaParams(0)));

        System.out.println(delta.decode().map(Set::size));
        System.out.println(delta.toProtoMessage().getSerializedSize());
        Assert.assertEquals(delta, KeySetDelta.fromProto(delta.toProtoMessage()));
    }

    @Test
    public void testMaximumLikelihoodDensityEstimate() {
        for (int n = 0; n <= 10; n++) {
            for (int m = 0; m <= 10 - n; m++) {
                System.out.println("n = " + n + ", m = " + m + ", densityEstimate = " +
                        KeySetDelta.maximumLikelihoodDensityEstimate(10, n, m));
            }
        }
    }

    @Test
    public void testKeyDecode() {
        System.out.println(Integer.toHexString(641));
        System.out.println(Integer.toHexString(6700417));
        System.out.println(Math.exp(-32768.0 / 4096) * 4096);
        new Random(12345).longs().limit(100).forEach(x -> {
            long y = x * 641 * -0x0ffffffffL;
            long z = y * 6700417;
            System.out.println(x + " " + y + " " + z);
        });
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        rnd.nextBytes(bytes);
        return bytes;
    }

    // SecureRandom seems to have pretty bad performance on Windows. This is much faster than rnd.longs()
    // and just as high a quality source of random numbers if the PRF security claims of SipHash are true.
    private LongStream longs(long n) {
        //noinspection UnstableApiUsage
        HashFunction fn = Hashing.sipHash24(rnd.nextLong(), rnd.nextLong());
        return LongStream.range(0, n).map(x -> fn.hashLong(x).asLong());
    }
}

package bisq.common.crypto;

import org.apache.commons.lang3.RandomStringUtils;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HashCashServiceTest {
    private final static Logger log = LoggerFactory.getLogger(HashCashServiceTest.class);

    @Test
    public void testNumberOfLeadingZeros() {
        assertEquals(8, HashCashService.numberOfLeadingZeros((byte) 0x0));
        assertEquals(0, HashCashService.numberOfLeadingZeros((byte) 0xFF));
        assertEquals(6, HashCashService.numberOfLeadingZeros((byte) 0x2));
        assertEquals(2, HashCashService.numberOfLeadingZeros(Byte.parseByte("00100000", 2)));
        assertEquals(1, HashCashService.numberOfLeadingZeros(new byte[]{Byte.parseByte("01000000", 2), Byte.parseByte("00000000", 2)}));
        assertEquals(9, HashCashService.numberOfLeadingZeros(new byte[]{Byte.parseByte("00000000", 2), Byte.parseByte("01000000", 2)}));
        assertEquals(17, HashCashService.numberOfLeadingZeros(new byte[]{Byte.parseByte("00000000", 2), Byte.parseByte("00000000", 2), Byte.parseByte("01000000", 2)}));
        assertEquals(9, HashCashService.numberOfLeadingZeros(new byte[]{Byte.parseByte("00000000", 2), Byte.parseByte("01010000", 2)}));
    }

    @Test
    public void testToNumLeadingZeros() {
        assertEquals(0, HashCashService.toNumLeadingZeros(-1.0));
        assertEquals(0, HashCashService.toNumLeadingZeros(0.0));
        assertEquals(0, HashCashService.toNumLeadingZeros(1.0));
        assertEquals(1, HashCashService.toNumLeadingZeros(1.1));
        assertEquals(1, HashCashService.toNumLeadingZeros(2.0));
        assertEquals(8, HashCashService.toNumLeadingZeros(256.0));
        assertEquals(1024, HashCashService.toNumLeadingZeros(Double.POSITIVE_INFINITY));
    }

    // @Ignore
    @Test
    public void testDiffIncrease() throws ExecutionException, InterruptedException {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            run(i, stringBuilder);
        }
        log.info(stringBuilder.toString());

        //Test result on a 4 GHz Intel Core i7:
        //Minting 1000 tokens with 0 leading zeros  took 0.281 ms per token and 2 iterations in average. Verification took 0.014 ms per token.
        //Minting 1000 tokens with 1 leading zeros  took 0.058 ms per token and 4 iterations in average. Verification took 0.005 ms per token.
        //Minting 1000 tokens with 2 leading zeros  took 0.081 ms per token and 8 iterations in average. Verification took 0.004 ms per token.
        //Minting 1000 tokens with 3 leading zeros  took 0.178 ms per token and 16 iterations in average. Verification took 0.003 ms per token.
        //Minting 1000 tokens with 4 leading zeros  took 0.133 ms per token and 34 iterations in average. Verification took 0.004 ms per token.
        //Minting 1000 tokens with 5 leading zeros  took 0.214 ms per token and 64 iterations in average. Verification took 0.003 ms per token.
        //Minting 1000 tokens with 6 leading zeros  took 0.251 ms per token and 126 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 7 leading zeros  took 0.396 ms per token and 245 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 8 leading zeros  took 0.835 ms per token and 529 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 9 leading zeros  took 1.585 ms per token and 1013 iterations in average. Verification took 0.001 ms per token.
        //Minting 1000 tokens with 10 leading zeros  took 3.219 ms per token and 2112 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 11 leading zeros  took 6.213 ms per token and 4123 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 12 leading zeros  took 13.3 ms per token and 8871 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 13 leading zeros  took 25.276 ms per token and 16786 iterations in average. Verification took 0.002 ms per token.
    }

    private void run(int log2Difficulty, StringBuilder stringBuilder) throws ExecutionException, InterruptedException {
        double difficulty = Math.scalb(1.0, log2Difficulty);
        int numTokens = 1000;
        byte[] payload = RandomStringUtils.random(50, true, true).getBytes(StandardCharsets.UTF_8);
        long ts = System.currentTimeMillis();
        List<ProofOfWork> tokens = new ArrayList<>();
        for (int i = 0; i < numTokens; i++) {
            byte[] challenge = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            tokens.add(new HashCashService().mint(payload, challenge, difficulty).get());
        }
        double size = tokens.size();
        long ts2 = System.currentTimeMillis();
        long averageCounter = Math.round(tokens.stream().mapToLong(ProofOfWork::getCounter).average().orElse(0));
        boolean allValid = tokens.stream().allMatch(new HashCashService()::verify);
        assertTrue(allValid);
        double time1 = (System.currentTimeMillis() - ts) / size;
        double time2 = (System.currentTimeMillis() - ts2) / size;
        stringBuilder.append("\nMinting ").append(numTokens)
                .append(" tokens with > ").append(log2Difficulty)
                .append(" leading zeros  took ").append(time1)
                .append(" ms per token and ").append(averageCounter)
                .append(" iterations in average. Verification took ").append(time2)
                .append(" ms per token.");
    }
}

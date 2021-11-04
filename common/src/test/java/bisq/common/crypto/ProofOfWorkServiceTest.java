package bisq.common.crypto;

import org.apache.commons.lang3.RandomStringUtils;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ProofOfWorkServiceTest {
    private final static Logger log = LoggerFactory.getLogger(ProofOfWorkServiceTest.class);

    // @Ignore
    @Test
    public void testDiffIncrease() throws ExecutionException, InterruptedException {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            run(i, stringBuilder);
        }
        log.info(stringBuilder.toString());

        //Test result on a 4 GHz Intel Core i7:
        //Minting 1000 tokens with 0 leading zeros  took 0.279 ms per token and 2 iterations in average. Verification took 0.025 ms per token.
        //Minting 1000 tokens with 1 leading zeros  took 0.063 ms per token and 4 iterations in average. Verification took 0.007 ms per token.
        //Minting 1000 tokens with 2 leading zeros  took 0.074 ms per token and 8 iterations in average. Verification took 0.004 ms per token.
        //Minting 1000 tokens with 3 leading zeros  took 0.117 ms per token and 16 iterations in average. Verification took 0.003 ms per token.
        //Minting 1000 tokens with 4 leading zeros  took 0.116 ms per token and 33 iterations in average. Verification took 0.003 ms per token.
        //Minting 1000 tokens with 5 leading zeros  took 0.204 ms per token and 65 iterations in average. Verification took 0.003 ms per token.
        //Minting 1000 tokens with 6 leading zeros  took 0.23 ms per token and 131 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 7 leading zeros  took 0.445 ms per token and 270 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 8 leading zeros  took 0.856 ms per token and 530 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 9 leading zeros  took 1.629 ms per token and 988 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 10 leading zeros  took 3.291 ms per token and 2103 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 11 leading zeros  took 6.259 ms per token and 4009 iterations in average. Verification took 0.001 ms per token.
        //Minting 1000 tokens with 12 leading zeros  took 13.845 ms per token and 8254 iterations in average. Verification took 0.002 ms per token.
        //Minting 1000 tokens with 13 leading zeros  took 26.052 ms per token and 16645 iterations in average. Verification took 0.002 ms per token.

        //Minting 100 tokens with 14 leading zeros  took 69.14 ms per token and 40917 iterations in average. Verification took 0.06 ms per token.
        //Minting 100 tokens with 15 leading zeros  took 102.14 ms per token and 65735 iterations in average. Verification took 0.01 ms per token.
        //Minting 100 tokens with 16 leading zeros  took 209.44 ms per token and 135137 iterations in average. Verification took 0.01 ms per token.
        //Minting 100 tokens with 17 leading zeros  took 409.46 ms per token and 263751 iterations in average. Verification took 0.01 ms per token.
        //Minting 100 tokens with 18 leading zeros  took 864.21 ms per token and 555671 iterations in average. Verification took 0.0 ms per token.
        //Minting 100 tokens with 19 leading zeros  took 1851.33 ms per token and 1097760 iterations in average. Verification took 0.0 ms per token.
    }

    private void run(int numLeadingZeros, StringBuilder stringBuilder) throws ExecutionException, InterruptedException {
        int numTokens = 1000;
        BigInteger target = ProofOfWorkService.getTarget(numLeadingZeros);
        byte[] payload = RandomStringUtils.random(50, true, true).getBytes(StandardCharsets.UTF_8);
        long ts = System.currentTimeMillis();
        List<ProofOfWork> tokens = new ArrayList<>();
        for (int i = 0; i < numTokens; i++) {
            byte[] challenge = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            tokens.add(ProofOfWorkService.mint(payload, challenge, target).get());
        }
        double size = tokens.size();
        long ts2 = System.currentTimeMillis();
        long averageCounter = Math.round(tokens.stream().mapToLong(ProofOfWork::getCounter).average().orElse(0));
        boolean allValid = tokens.stream().allMatch(ProofOfWorkService::verify);
        assertTrue(allValid);
        double time1 = (System.currentTimeMillis() - ts) / size;
        double time2 = (System.currentTimeMillis() - ts2) / size;
        stringBuilder.append("\nMinting ").append(numTokens)
                .append(" tokens with ").append(numLeadingZeros)
                .append(" leading zeros  took ").append(time1)
                .append(" ms per token and ").append(averageCounter)
                .append(" iterations in average. Verification took ").append(time2)
                .append(" ms per token.");
    }
}

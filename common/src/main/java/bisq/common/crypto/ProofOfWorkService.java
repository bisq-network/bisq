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

import com.google.common.primitives.Longs;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import lombok.extern.slf4j.Slf4j;

/**
 * Bitcoin-like proof of work implementation. Differs from original hashcash by using BigInteger for comparing
 * the hash result with the target difficulty to gain more fine grained control for difficulty adjustment.
 * This class provides a convenience method getDifficultyAsBigInteger(numLeadingZeros) to get values which are
 * equivalent to the hashcash difficulty values.
 *
 * See https://en.wikipedia.org/wiki/Hashcash"
 * "Unlike hashcash, Bitcoin's difficulty target does not specify a minimum number of leading zeros in the hash.
 * Instead, the hash is interpreted as a (very large) integer, and this integer must be less than the target integer."
 */
@Slf4j
public class ProofOfWorkService {
    // Default validations. Custom implementations might use tolerance.
    private static final BiFunction<byte[], byte[], Boolean> isChallengeValid = Arrays::equals;
    private static final BiFunction<BigInteger, BigInteger, Boolean> isTargetValid = BigInteger::equals;

    public static CompletableFuture<ProofOfWork> mint(byte[] payload,
                                                      byte[] challenge,
                                                      BigInteger target) {
        return mint(payload,
                challenge,
                target,
                ProofOfWorkService::testTarget);
    }

    public static boolean verify(ProofOfWork proofOfWork) {
        return verify(proofOfWork,
                proofOfWork.getChallenge(),
                proofOfWork.getTarget());
    }

    public static boolean verify(ProofOfWork proofOfWork,
                                 byte[] controlChallenge,
                                 BigInteger controlTarget) {
        return verify(proofOfWork,
                controlChallenge,
                controlTarget,
                ProofOfWorkService::testTarget);
    }

    public static boolean verify(ProofOfWork proofOfWork,
                                 byte[] controlChallenge,
                                 BigInteger controlTarget,
                                 BiFunction<byte[], byte[], Boolean> challengeValidation,
                                 BiFunction<BigInteger, BigInteger, Boolean> targetValidation) {
        return verify(proofOfWork,
                controlChallenge,
                controlTarget,
                challengeValidation,
                targetValidation,
                ProofOfWorkService::testTarget);

    }

    public static BigInteger getTarget(int numLeadingZeros) {
        return BigInteger.TWO.pow(255 - numLeadingZeros).subtract(BigInteger.ONE);
    }

    private static boolean testTarget(byte[] result, BigInteger target) {
        return getUnsignedBigInteger(result).compareTo(target) < 0;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Generic
    ///////////////////////////////////////////////////////////////////////////////////////////

    static CompletableFuture<ProofOfWork> mint(byte[] payload,
                                               byte[] challenge,
                                               BigInteger target,
                                               BiFunction<byte[], BigInteger, Boolean> testTarget) {
        return CompletableFuture.supplyAsync(() -> {
            long ts = System.currentTimeMillis();
            byte[] result;
            long counter = 0;
            do {
                result = toSha256Hash(payload, challenge, ++counter);
            }
            while (!testTarget.apply(result, target));
            return new ProofOfWork(payload, counter, challenge, target, System.currentTimeMillis() - ts);
        });
    }

    static boolean verify(ProofOfWork proofOfWork,
                          byte[] controlChallenge,
                          BigInteger controlTarget,
                          BiFunction<byte[], BigInteger, Boolean> testTarget) {
        return verify(proofOfWork,
                controlChallenge,
                controlTarget,
                ProofOfWorkService.isChallengeValid,
                ProofOfWorkService.isTargetValid,
                testTarget);
    }

    static boolean verify(ProofOfWork proofOfWork,
                          byte[] controlChallenge,
                          BigInteger controlTarget,
                          BiFunction<byte[], byte[], Boolean> challengeValidation,
                          BiFunction<BigInteger, BigInteger, Boolean> targetValidation,
                          BiFunction<byte[], BigInteger, Boolean> testTarget) {
        return challengeValidation.apply(proofOfWork.getChallenge(), controlChallenge) &&
                targetValidation.apply(proofOfWork.getTarget(), controlTarget) &&
                verify(proofOfWork, testTarget);
    }

    private static boolean verify(ProofOfWork proofOfWork, BiFunction<byte[], BigInteger, Boolean> testTarget) {
        byte[] hash = toSha256Hash(proofOfWork.getPayload(), proofOfWork.getChallenge(), proofOfWork.getCounter());
        return testTarget.apply(hash, proofOfWork.getTarget());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static BigInteger getUnsignedBigInteger(byte[] result) {
        return new BigInteger(1, result);
    }

    private static byte[] toSha256Hash(byte[] payload, byte[] challenge, long counter) {
        byte[] preImage = org.bouncycastle.util.Arrays.concatenate(payload,
                challenge,
                Longs.toByteArray(counter));
        return Hash.getSha256Hash(preImage);
    }
}

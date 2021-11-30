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

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class EquihashProofOfWorkService extends ProofOfWorkService {
    /** Rough cost of two Hashcash iterations compared to solving an Equihash-90-5 puzzle of unit difficulty. */
    private static final double DIFFICULTY_SCALE_FACTOR = 3.0e-5;

    EquihashProofOfWorkService(int version) {
        super(version);
    }

    @Override
    public CompletableFuture<ProofOfWork> mint(String itemId, byte[] challenge, double difficulty) {
        double scaledDifficulty = scaledDifficulty(difficulty);
        log.info("Got scaled & adjusted difficulty: {}", scaledDifficulty);

        return CompletableFuture.supplyAsync(() -> {
            long ts = System.currentTimeMillis();
            byte[] solution = new Equihash(90, 5, scaledDifficulty).puzzle(challenge).findSolution().serialize();
            long counter = Longs.fromByteArray(Arrays.copyOf(solution, 8));
            var proofOfWork = new ProofOfWork(solution, counter, challenge, difficulty,
                    System.currentTimeMillis() - ts, getVersion());
            log.info("Completed minting proofOfWork: {}", proofOfWork);
            return proofOfWork;
        });
    }

    @Override
    public byte[] getChallenge(String itemId, String ownerId) {
        checkArgument(!StringUtils.contains(itemId, '\0'));
        checkArgument(!StringUtils.contains(ownerId, '\0'));
        return Hash.getSha256Hash(checkNotNull(itemId) + "\0" + checkNotNull(ownerId));
    }

    @Override
    boolean verify(ProofOfWork proofOfWork) {
        double scaledDifficulty = scaledDifficulty(proofOfWork.getDifficulty());

        var puzzle = new Equihash(90, 5, scaledDifficulty).puzzle(proofOfWork.getChallenge());
        return puzzle.deserializeSolution(proofOfWork.getPayload()).verify();
    }

    private static double scaledDifficulty(double difficulty) {
        return Equihash.adjustDifficulty(DIFFICULTY_SCALE_FACTOR * difficulty);
    }
}

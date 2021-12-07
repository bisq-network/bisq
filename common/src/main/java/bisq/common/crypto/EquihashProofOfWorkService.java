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

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EquihashProofOfWorkService extends ProofOfWorkService {
    /** Rough cost of two Hashcash iterations compared to solving an Equihash-90-5 puzzle of unit difficulty. */
    private static final double DIFFICULTY_SCALE_FACTOR = 3.0e-5;

    EquihashProofOfWorkService(int version) {
        super(version);
    }

    @Override
    public CompletableFuture<ProofOfWork> mint(byte[] payload, byte[] challenge, double difficulty) {
        double scaledDifficulty = scaledDifficulty(difficulty);
        log.info("Got scaled & adjusted difficulty: {}", scaledDifficulty);

        return CompletableFuture.supplyAsync(() -> {
            long ts = System.currentTimeMillis();
            byte[] seed = getSeed(payload, challenge);
            byte[] solution = new Equihash(90, 5, scaledDifficulty).puzzle(seed).findSolution().serialize();
            long counter = Longs.fromByteArray(Arrays.copyOf(solution, 8));
            var proofOfWork = new ProofOfWork(payload, counter, challenge, difficulty,
                    System.currentTimeMillis() - ts, solution, getVersion());
            log.info("Completed minting proofOfWork: {}", proofOfWork);
            return proofOfWork;
        });
    }

    private byte[] getSeed(byte[] payload, byte[] challenge) {
        return Hash.getSha256Hash(Bytes.concat(payload, challenge));
    }

    @Override
    public byte[] getChallenge(String itemId, String ownerId) {
        String escapedItemId = itemId.replace(" ", "  ");
        String escapedOwnerId = ownerId.replace(" ", "  ");
        return Hash.getSha256Hash(escapedItemId + ", " + escapedOwnerId);
    }

    @Override
    boolean verify(ProofOfWork proofOfWork) {
        double scaledDifficulty = scaledDifficulty(proofOfWork.getDifficulty());

        byte[] seed = getSeed(proofOfWork.getPayload(), proofOfWork.getChallenge());
        var puzzle = new Equihash(90, 5, scaledDifficulty).puzzle(seed);
        return puzzle.deserializeSolution(proofOfWork.getSolution()).verify();
    }

    private static double scaledDifficulty(double difficulty) {
        return Equihash.adjustDifficulty(DIFFICULTY_SCALE_FACTOR * difficulty);
    }
}

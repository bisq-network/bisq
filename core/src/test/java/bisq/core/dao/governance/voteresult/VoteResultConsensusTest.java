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

package bisq.core.dao.governance.voteresult;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoteResultConsensusTest {
    private static final int PRE_ACTIVATION_HEIGHT = 954_199;
    private static final int ACTIVATION_HEIGHT = 954_200;
    private static final byte[] FIRST_HASH = new byte[]{1};
    private static final byte[] SECOND_HASH = new byte[]{2};

    @Test
    void getStakeOfAllMatchesStreamSumBeforeActivationForNormalValues() {
        List<VoteResultService.HashWithStake> hashWithStakeList = List.of(
                new VoteResultService.HashWithStake(FIRST_HASH, 7),
                new VoteResultService.HashWithStake(SECOND_HASH, 11),
                new VoteResultService.HashWithStake(new byte[]{3}, 13));

        long expected = hashWithStakeList.stream()
                .mapToLong(VoteResultService.HashWithStake::getStake)
                .sum();
        assertEquals(expected, VoteResultConsensus.getStakeOfAll(hashWithStakeList, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void getStakeOfAllUsesLegacyOverflowBeforeActivation() {
        List<VoteResultService.HashWithStake> hashWithStakeList = List.of(
                new VoteResultService.HashWithStake(FIRST_HASH, Long.MAX_VALUE),
                new VoteResultService.HashWithStake(SECOND_HASH, 1));

        assertEquals(Long.MIN_VALUE, VoteResultConsensus.getStakeOfAll(hashWithStakeList, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void getMajorityHashThrowsOnTotalStakeOverflowAtActivation() {
        List<VoteResultService.HashWithStake> hashWithStakeList = new ArrayList<>(List.of(
                new VoteResultService.HashWithStake(FIRST_HASH, Long.MAX_VALUE),
                new VoteResultService.HashWithStake(SECOND_HASH, 1)));

        assertThrows(ArithmeticException.class,
                () -> VoteResultConsensus.getMajorityHash(hashWithStakeList, ACTIVATION_HEIGHT));
    }

    @Test
    void getMajorityHashThrowsConsensusExceptionWhenNoSuperMajority() {
        List<VoteResultService.HashWithStake> hashWithStakeList = new ArrayList<>(List.of(
                new VoteResultService.HashWithStake(FIRST_HASH, 50),
                new VoteResultService.HashWithStake(SECOND_HASH, 50)));

        assertThrows(VoteResultException.ConsensusException.class,
                () -> VoteResultConsensus.getMajorityHash(hashWithStakeList, ACTIVATION_HEIGHT));
    }

    @Test
    void getMajorityHashReturnsWinnerWhenSuperMajorityReached() throws Exception {
        List<VoteResultService.HashWithStake> hashWithStakeList = new ArrayList<>(List.of(
                new VoteResultService.HashWithStake(FIRST_HASH, 81),
                new VoteResultService.HashWithStake(SECOND_HASH, 19)));

        assertArrayEquals(FIRST_HASH, VoteResultConsensus.getMajorityHash(hashWithStakeList, ACTIVATION_HEIGHT));
    }
}

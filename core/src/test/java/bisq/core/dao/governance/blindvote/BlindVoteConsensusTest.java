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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.blindvote;

import bisq.core.dao.DaoHardFork;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlindVoteConsensusTest {
    private static final String BLIND_VOTE_TX_ID =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void equalTxIdsPreserveInputOrderBeforeMeritDecryptabilityActivation() {
        BlindVote first = blindVote((byte) 0x01);
        BlindVote second = blindVote((byte) 0x02);
        List<BlindVote> input = List.of(second, first);

        List<BlindVote> result = BlindVoteConsensus.getSortedBlindVoteListOfCycle(input,
                DaoHardFork.getBlindVoteMeritDecryptabilityActivationHeight() - 1);

        assertEquals(input, result);
    }

    @Test
    void equalTxIdsUseCanonicalPayloadOrderFromMeritDecryptabilityActivation() {
        BlindVote first = blindVote((byte) 0x01);
        BlindVote second = blindVote((byte) 0x02);
        List<BlindVote> expected = Arrays.compareUnsigned(first.encodeCanonical(), second.encodeCanonical()) < 0
                ? List.of(first, second)
                : List.of(second, first);

        List<BlindVote> forward = BlindVoteConsensus.getSortedBlindVoteListOfCycle(List.of(first, second),
                DaoHardFork.getBlindVoteMeritDecryptabilityActivationHeight());
        List<BlindVote> reverse = BlindVoteConsensus.getSortedBlindVoteListOfCycle(List.of(second, first),
                DaoHardFork.getBlindVoteMeritDecryptabilityActivationHeight());

        assertEquals(expected, forward);
        assertEquals(expected, reverse);
    }

    private static BlindVote blindVote(byte marker) {
        return new BlindVote(new byte[]{marker},
                BLIND_VOTE_TX_ID,
                123_456,
                new byte[]{marker},
                1_700_000_000_000L + marker);
    }
}

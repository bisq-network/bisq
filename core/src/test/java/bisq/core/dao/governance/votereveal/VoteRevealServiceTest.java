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

package bisq.core.dao.governance.votereveal;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoHardFork;
import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.DaoPhase;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoteRevealServiceTest {
    private static final String BLIND_VOTE_TX_ID =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void blindVoteListOrderingUsesResultEvaluationHeight() {
        int resultEvaluationHeight = DaoHardFork.getBlindVoteMeritDecryptabilityActivationHeight();
        int revealHeight = resultEvaluationHeight - 10;
        BlindVote first = blindVote((byte) 0x01);
        BlindVote second = blindVote((byte) 0x02);
        List<BlindVote> canonicalOrder = BlindVoteConsensus.getSortedBlindVoteListOfCycle(
                List.of(first, second), resultEvaluationHeight);
        List<BlindVote> reverseOrder = List.of(canonicalOrder.get(1), canonicalOrder.get(0));

        BlindVoteListService blindVoteListService = mock(BlindVoteListService.class);
        PeriodService periodService = mock(PeriodService.class);
        when(blindVoteListService.getBlindVotesInPhaseAndCycle()).thenReturn(reverseOrder);
        when(periodService.getFirstBlockOfPhase(revealHeight, DaoPhase.Phase.RESULT))
                .thenReturn(resultEvaluationHeight);
        VoteRevealService voteRevealService = new VoteRevealService(mock(DaoStateService.class),
                blindVoteListService,
                periodService,
                mock(MyVoteListService.class),
                mock(BsqWalletService.class),
                mock(BtcWalletService.class),
                mock(WalletsManager.class));

        byte[] result = voteRevealService.getHashOfBlindVoteList(revealHeight);

        assertArrayEquals(VoteRevealConsensus.getHashOfBlindVoteList(canonicalOrder), result);
    }

    private static BlindVote blindVote(byte marker) {
        return new BlindVote(new byte[]{marker},
                BLIND_VOTE_TX_ID,
                123_456,
                new byte[]{marker},
                1_700_000_000_000L + marker);
    }
}

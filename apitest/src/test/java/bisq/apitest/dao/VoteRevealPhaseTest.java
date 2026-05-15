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

package bisq.apitest.dao;

import bisq.proto.grpc.DaoPhaseEnum;
import bisq.proto.grpc.MyVoteInfo;
import bisq.proto.grpc.ProposalInfo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * VOTE_REVEAL phase: the reveal tx is broadcast automatically once the chain enters
 * the reveal phase. When no blind vote was published the reveal phase is a silent no-op.
 */
public class VoteRevealPhaseTest extends DaoTestBase {

    @Test
    public void revealAutoPublished() {
        // 1. Setup: proposal phase → create proposal + vote → blind vote → reveal phase.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createGenericProposal("rev", "https://example.com/rev");
        dao.confirmTx(p.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        alice.setVote(p.getTxId(), "accept");
        String bv = alice.publishBlindVote(10_000L);
        dao.confirmTx(bv);

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        // 2. Bisq's VoteRevealService auto-broadcasts the reveal tx on chain entering
        //    VOTE_REVEAL. Inject + confirm deterministically.
        java.util.List<String> revealed = dao.confirmAutoRevealsFor(alice);
        assertFalse(revealed.isEmpty(), "alice should produce a reveal tx");

        // 3. revealTxId set on the MyVote entry.
        MyVoteInfo mv = alice.getMyVotes().getVotesList().get(0);
        assertFalse(mv.getRevealTxId().isEmpty(), "reveal tx id must be set after reveal phase");
    }

    @Test
    public void noRevealWhenNoBlindVote() {
        // Cycle through proposal & blind vote without publishing → reveal phase is a no-op.
        // Use a fresh proposal-only cycle: counts MyVote entries scoped to current cycle.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        // No publish — no MyVote should exist in current cycle.
        assertEquals(0, alice.getMyVotes().getVotesCount(),
                "no MyVote should exist when nothing was published this cycle");

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.generateBlocks(2);

        // After reveal phase: still 0. VoteRevealService must not synthesize a MyVote.
        assertEquals(0, alice.getMyVotes().getVotesCount(),
                "reveal phase must not create MyVote entries from nothing");
    }
}

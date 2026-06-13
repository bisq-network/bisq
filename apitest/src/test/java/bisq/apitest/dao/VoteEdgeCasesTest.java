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
import bisq.proto.grpc.EvaluatedProposalInfo;
import bisq.proto.grpc.GetVoteResultsReply;
import bisq.proto.grpc.ProposalInfo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge cases around vote tabulation: no votes, single-voter quorum, abstain-only, etc.
 */
public class VoteEdgeCasesTest extends DaoTestBase {

    @Test
    public void noVotes_proposalRejected() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createGenericProposal("ev-novotes", "https://x.test/nv");
        dao.confirmTx(p.getTxId());
        // No setVote/publishBlindVote. Just push through the cycle.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        // No blind votes published → no reveal txs expected; just push past the phase.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);

        // Bisq's VoteResultService SKIPS evaluation entirely when there are no
        // decrypted ballots — it does not produce a rejected EvaluatedProposal.
        // The expected behavior is therefore: proposal is NOT in the evaluated list
        // (i.e. functionally rejected because nothing accepted it).
        boolean present = alice.getVoteResults(-1).getEvaluatedProposalsList().stream()
                .anyMatch(e -> e.getProposal().getTxId().equals(p.getTxId()));
        assertFalse(present,
                "no-vote proposal must not be in evaluated list (treated as rejected)");
    }

    @Test
    public void allIgnoreVotes_proposalRejected() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createGenericProposal("ev-ignore", "https://x.test/ig");
        dao.confirmTx(p.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        DaoTestUtils.await(() -> bob.getBallots().getBallotsList().stream()
                        .anyMatch(b -> b.getProposal().getTxId().equals(p.getTxId())),
                60_000, "bob sees ballot");
        alice.setVote(p.getTxId(), "ignore");
        bob.setVote(p.getTxId(), "ignore");
        String aBv = alice.publishBlindVote(50_000L);
        String bBv = bob.publishBlindVote(50_000L);
        dao.confirmTx(alice, aBv);
        dao.confirmTx(bob, bBv);
        dao.awaitBlindVotePropagation(alice, 2, "alice");
        dao.awaitBlindVotePropagation(bob, 2, "bob");
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.confirmAutoRevealsForAll(alice, bob);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);

        EvaluatedProposalInfo eval = find(p.getTxId());
        assertFalse(eval.getIsAccepted(), "all ignore → rejected (no active votes)");
        assertEquals(0, eval.getNumAcceptedVotes());
        assertEquals(0, eval.getNumRejectedVotes());
        assertTrue(eval.getNumIgnoredVotes() >= 2);
    }

    @Test
    public void singleVoter_acceptedWithSufficientStake() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createGenericProposal("ev-single", "https://x.test/s");
        dao.confirmTx(p.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        alice.setVote(p.getTxId(), "accept");
        // QUORUM_GENERIC default = 500_000 BSQ-sats. 1_000_000 sat stake clears quorum,
        // single ACCEPT vote = 100% > THRESHOLD_GENERIC (50%). Expect ACCEPTED.
        String bv = alice.publishBlindVote(1_000_000L);
        dao.confirmTx(bv);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.confirmAutoRevealsFor(alice);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);

        EvaluatedProposalInfo eval = find(p.getTxId());
        assertEquals(1, eval.getNumAcceptedVotes());
        assertEquals(0, eval.getNumRejectedVotes());
        assertTrue(eval.getIsAccepted(),
                "single voter above quorum with 100% accept must be accepted");
    }

    @Test
    public void changingVoteAfterPublishing_doesNotAffectChainResult() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createGenericProposal("ev-late", "https://x.test/late");
        dao.confirmTx(p.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        alice.setVote(p.getTxId(), "accept");
        String bv = alice.publishBlindVote(100_000L);
        // Change vote after the encrypted blind vote tx is on-chain; the on-chain payload is sealed,
        // so this local change must not affect the eventual result.
        alice.setVote(p.getTxId(), "reject");
        dao.confirmTx(bv);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.confirmAutoRevealsFor(alice);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);

        EvaluatedProposalInfo eval = find(p.getTxId());
        assertEquals(1, eval.getNumAcceptedVotes(),
                "post-publish setVote must not flip the on-chain blind vote");
    }

    private EvaluatedProposalInfo find(String txId) {
        GetVoteResultsReply reply = alice.getVoteResults(-1);
        return reply.getEvaluatedProposalsList().stream()
                .filter(e -> e.getProposal().getTxId().equals(txId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no eval for " + txId));
    }
}

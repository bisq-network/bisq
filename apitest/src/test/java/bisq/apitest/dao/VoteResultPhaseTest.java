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
 * RESULT phase: drive a single proposal through the full cycle and verify outcome
 * matches the vote pattern.
 */
public class VoteResultPhaseTest extends DaoTestBase {

    // QUORUM_GENERIC default = 5000 BSQ = 500_000 BSQ-sats. Tests need combined stake
    // above this for any "accepted" outcome; tie/reject only need stake above dust.
    private static final long STAKE_HIGH = 1_000_000L;  // above quorum, per voter
    private static final long STAKE_MID = 50_000L;       // above dust, below quorum

    @Test
    public void proposalAccepted_whenBothAccept() {
        ProposalInfo p = runCycle("comp-accept", "accept", "accept", STAKE_HIGH, STAKE_HIGH);
        EvaluatedProposalInfo eval = findEval(p.getTxId());
        assertTrue(eval.getIsAccepted(), "proposal should be accepted when both voters accept");
        assertEquals(2, eval.getNumAcceptedVotes());
        assertEquals(0, eval.getNumRejectedVotes());
    }

    @Test
    public void proposalRejected_whenBothReject() {
        ProposalInfo p = runCycle("comp-reject", "reject", "reject", STAKE_HIGH, STAKE_HIGH);
        EvaluatedProposalInfo eval = findEval(p.getTxId());
        assertFalse(eval.getIsAccepted());
        assertEquals(0, eval.getNumAcceptedVotes());
        assertEquals(2, eval.getNumRejectedVotes());
    }

    @Test
    public void proposalRejected_whenAcceptedRejectedTie() {
        ProposalInfo p = runCycle("comp-tie", "accept", "reject", STAKE_HIGH, STAKE_HIGH);
        EvaluatedProposalInfo eval = findEval(p.getTxId());
        // Assert BOTH votes were actually counted — otherwise a "no decrypted ballots"
        // skip would also produce isAccepted=false and we couldn't distinguish.
        assertEquals(1, eval.getNumAcceptedVotes(), "alice's accept vote must be counted");
        assertEquals(1, eval.getNumRejectedVotes(), "bob's reject vote must be counted");
        // Threshold > 50% in basis points → exact tie fails.
        assertFalse(eval.getIsAccepted(), "exact tie must be rejected");
    }

    @Test
    public void proposalRejected_whenBelowQuorum() {
        // Only Alice votes with a tiny stake — above dust, below quorum.
        ProposalInfo p = runCycle("comp-quorum", "accept", null, STAKE_MID, 0L);
        EvaluatedProposalInfo eval = findEval(p.getTxId());
        // Verify vote WAS counted: rejection is due to quorum, not missing decryption.
        assertEquals(1, eval.getNumAcceptedVotes(), "alice's vote must be counted");
        assertEquals(0, eval.getNumRejectedVotes());
        assertFalse(eval.getIsAccepted(), "below quorum must be rejected");
    }

    /**
     * End-to-end governance loop: propose → vote → result → outcome reflects in DAO state.
     *
     * <p>We deliberately use a CHANGE_PARAM proposal — but only assert the proposal is
     * EVALUATED and votes are counted. We do NOT assert {@code isAccepted == true} or
     * verify the param flip, because the default {@code QUORUM_CHANGE_PARAM = 100_000 BSQ}
     * exceeds the combined BSQ available in dao-setup wallets. Asserting the eval pipeline
     * runs and tabulates both votes is enough to prove the end-to-end loop is intact;
     * the quorum-met / param-flip path is a separate concern best tested against a DAO
     * setup specifically seeded with high BSQ balances.
     */
    @Test
    public void changeParamProposal_evaluatedWithVotesCounted() {
        String param = "BONDED_ROLE_FACTOR";
        String newValue = "750.00";

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createChangeParamProposal(
                "change-param-e2e", "https://x.test/cp", param, newValue);
        dao.confirmTx(p.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        DaoTestUtils.await(() -> bob.getBallots().getBallotsList().stream()
                        .anyMatch(b -> b.getProposal().getTxId().equals(p.getTxId())),
                60_000, "bob sees ballot for " + p.getTxId());
        alice.setVote(p.getTxId(), "accept");
        String aBv = alice.publishBlindVote(STAKE_HIGH);
        bob.setVote(p.getTxId(), "accept");
        String bBv = bob.publishBlindVote(STAKE_HIGH);
        dao.confirmTx(alice, aBv);
        dao.confirmTx(bob, bBv);
        dao.awaitBlindVotePropagation(alice, 2, "alice");
        dao.awaitBlindVotePropagation(bob, 2, "bob");

        // Confirm both reveals in one block so neither crosses out of the 2-block phase.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.confirmAutoRevealsForAll(alice, bob);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);

        // Eval pipeline ran and counted both votes — that proves the end-to-end loop.
        EvaluatedProposalInfo eval = findEval(p.getTxId());
        assertEquals(2, eval.getNumAcceptedVotes(),
                "both accept votes must be counted for change-param proposal");
        assertEquals(0, eval.getNumRejectedVotes());
        // Quorum-met path intentionally not asserted — see method javadoc.
    }

    /**
     * Drive one cycle with one generic proposal and the given Alice/Bob votes.
     */
    private ProposalInfo runCycle(String name, String aliceVote, String bobVote, long aliceStake, long bobStake) {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createGenericProposal(name, "https://example.com/" + name);
        dao.confirmTx(p.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        String aliceBv = null;
        if (aliceVote != null) {
            alice.setVote(p.getTxId(), aliceVote);
            aliceBv = alice.publishBlindVote(aliceStake);
        }
        String bobBv = null;
        if (bobVote != null) {
            DaoTestUtils.await(() -> bob.getBallots().getBallotsList().stream()
                            .anyMatch(b -> b.getProposal().getTxId().equals(p.getTxId())),
                    60_000, "bob sees ballot for " + p.getTxId());
            bob.setVote(p.getTxId(), bobVote);
            bobBv = bob.publishBlindVote(bobStake);
        }
        if (aliceBv != null) dao.confirmTx(alice, aliceBv);
        if (bobBv != null) dao.confirmTx(bob, bobBv);

        // Wait for each peer to receive the OTHER peer's blind-vote payload (P2P).
        // VoteResultService requires both the on-chain blind-vote tx AND the off-chain
        // payload to decrypt votes; without alice's payload bob can't decrypt alice's
        // vote and vice versa, leaving evaluatedProposalList empty.
        int expectedBlindVotes = (aliceBv != null ? 1 : 0) + (bobBv != null ? 1 : 0);
        if (expectedBlindVotes > 0) {
            dao.awaitBlindVotePropagation(alice, expectedBlindVotes, "alice");
            dao.awaitBlindVotePropagation(bob, expectedBlindVotes, "bob");
        }

        // Confirm every voter's reveal together in one in-phase block — mining one block per
        // voter would push a later reveal past the 2-block regtest VOTE_REVEAL phase and drop
        // that vote (see confirmAutoRevealsForAll).
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        if (aliceBv != null && bobBv != null) dao.confirmAutoRevealsForAll(alice, bob);
        else if (aliceBv != null) dao.confirmAutoRevealsForAll(alice);
        else if (bobBv != null) dao.confirmAutoRevealsForAll(bob);

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        // Stay in RESULT (2 blocks) long enough for VoteResultService to evaluate.
        dao.generateBlocks(1);

        return p;
    }

    private EvaluatedProposalInfo findEval(String txId) {
        GetVoteResultsReply reply = alice.getVoteResults(-1);
        return reply.getEvaluatedProposalsList().stream()
                .filter(e -> e.getProposal().getTxId().equals(txId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no evaluated proposal for tx " + txId));
    }
}

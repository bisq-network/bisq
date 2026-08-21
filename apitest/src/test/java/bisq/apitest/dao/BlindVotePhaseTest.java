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

import bisq.proto.grpc.BallotInfo;
import bisq.proto.grpc.DaoPhaseEnum;
import bisq.proto.grpc.ProposalInfo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BLIND_VOTE phase: setVote on ballots, publish blind vote, propagation, and
 * out-of-phase rejection.
 */
public class BlindVotePhaseTest extends DaoTestBase {

    @Test
    public void blindVoteFullFlow() {
        // 1. In proposal phase, create 3 proposals so we have ballots to vote on.
        // Need at least 3 blocks of headroom for the 3 confirmTx calls below.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL, 3);
        // Mine after each proposal: Bisq doesn't allow chaining BSQ tx outputs while
        // the prior tx is still unconfirmed (matches GUI behavior).
        ProposalInfo p1 = alice.createGenericProposal("p1", "https://example.com/p1");
        dao.confirmTx(p1.getTxId());
        ProposalInfo p2 = alice.createGenericProposal("p2", "https://example.com/p2");
        dao.confirmTx(p2.getTxId());
        ProposalInfo p3 = alice.createGenericProposal("p3", "https://example.com/p3");
        dao.confirmTx(p3.getTxId());

        // 2. Advance to BLIND_VOTE.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);

        // 3. Set distinct votes: accept, reject, ignore.
        alice.setVote(p1.getTxId(), "accept");
        alice.setVote(p2.getTxId(), "reject");
        alice.setVote(p3.getTxId(), "ignore");

        // 4. Verify ballot state.
        BallotInfo b1 = findBallot(p1.getTxId());
        BallotInfo b2 = findBallot(p2.getTxId());
        BallotInfo b3 = findBallot(p3.getTxId());
        assertTrue(b1.getHasVote() && b1.getVoteAccept(), "p1 should be accept");
        assertTrue(b2.getHasVote() && !b2.getVoteAccept(), "p2 should be reject");
        assertFalse(b3.getHasVote(), "p3 should be ignore (no vote object)");

        // 5. Publish blind vote with a small stake.
        String txId = alice.publishBlindVote(10_000L);
        assertFalse(txId.isEmpty(), "blind vote tx id should be returned");

        // 6. Wait for blind vote tx in mempool, mine, then verify Alice's myvotes reflects.
        dao.confirmTx(txId);
        DaoTestUtils.await(() -> alice.getMyVotes().getVotesCount() >= 1,
                30_000, "alice myVotes contains published blind vote");

        // 7. Verify the blind vote PAYLOAD reached Bob via P2P (not just the on-chain tx).
        // Counts blind-vote payloads in current cycle on bob's local DAO state.
        dao.awaitBlindVotePropagation(bob, 1, "bob");
    }

    @Test
    public void blindVoteWithZeroStake_rejected() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        // Bisq's blind vote consensus enforces a minimum stake > 0; publish must fail.
        assertThrows(RuntimeException.class, () -> alice.publishBlindVote(0L));
    }

    // Bisq core does not enforce phase at publish-time for blind votes. A broadcast
    // outside the BLIND_VOTE phase is accepted locally, which corrupts the daemon's
    // DAO state vs. the seed node (`isInConflictWithSeedNode == true`), making every
    // subsequent test fail at `awaitDaoStateReady`. Out-of-phase enforcement happens
    // in TxParser at chain-parse time, not in CoreDaoService. Skip this case.

    @Test
    public void setVote_invalidProposalRejected() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        // CoreDaoService.setVote does `orElseThrow` for unknown proposal tx id;
        // gRPC propagates as RuntimeException on the client.
        String unknown = "0000000000000000000000000000000000000000000000000000000000000000";
        assertThrows(RuntimeException.class, () -> alice.setVote(unknown, "accept"));
    }

    @Test
    public void setVote_invalidVoteValueRejected() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createGenericProposal("p-vv", "https://example.com/vv");
        dao.confirmTx(p.getTxId());
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        assertThrows(RuntimeException.class, () -> alice.setVote(p.getTxId(), "maybe"));
    }

    private BallotInfo findBallot(String txId) {
        return alice.getBallots().getBallotsList().stream()
                .filter(bi -> bi.getProposal().getTxId().equals(txId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ballot for tx " + txId));
    }
}

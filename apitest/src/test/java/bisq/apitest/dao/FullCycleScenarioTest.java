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

import bisq.cli.GrpcClient;

import bisq.proto.grpc.DaoPhaseEnum;
import bisq.proto.grpc.EvaluatedProposalInfo;
import bisq.proto.grpc.GetVoteResultsReply;
import bisq.proto.grpc.ProposalInfo;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-cycle tests: multiple proposals, mixed votes, two consecutive cycles, etc.
 */
public class FullCycleScenarioTest extends DaoTestBase {

    @Test
    public void multipleProposals_independentOutcomes() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL, 3);
        // Mine between each: unconfirmed BSQ change can't be chained (matches GUI).
        ProposalInfo accept = alice.createGenericProposal("multi-accept", "https://x.test/a");
        dao.confirmTx(accept.getTxId());
        ProposalInfo reject = alice.createGenericProposal("multi-reject", "https://x.test/r");
        dao.confirmTx(reject.getTxId());
        ProposalInfo tie = alice.createGenericProposal("multi-tie", "https://x.test/t");
        dao.confirmTx(tie.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        DaoTestUtils.await(() -> bob.getBallots().getBallotsCount() >= 3,
                60_000, "bob sees all 3 ballots");

        alice.setVote(accept.getTxId(), "accept");
        alice.setVote(reject.getTxId(), "reject");
        alice.setVote(tie.getTxId(), "accept");
        String aliceBv = alice.publishBlindVote(1_000_000L);

        bob.setVote(accept.getTxId(), "accept");
        bob.setVote(reject.getTxId(), "reject");
        bob.setVote(tie.getTxId(), "reject");
        String bobBv = bob.publishBlindVote(1_000_000L);

        dao.confirmTx(alice, aliceBv);
        dao.confirmTx(bob, bobBv);
        dao.awaitBlindVotePropagation(alice, 2, "alice");
        dao.awaitBlindVotePropagation(bob, 2, "bob");
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.confirmAutoRevealsFor(alice);
        dao.confirmAutoRevealsFor(bob);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);

        GetVoteResultsReply results = alice.getVoteResults(-1);
        EvaluatedProposalInfo evAccept = findIn(results, accept.getTxId());
        EvaluatedProposalInfo evReject = findIn(results, reject.getTxId());
        EvaluatedProposalInfo evTie = findIn(results, tie.getTxId());
        assertEquals(true, evAccept.getIsAccepted(), "both accept → accepted");
        assertEquals(false, evReject.getIsAccepted(), "both reject → rejected");
        assertEquals(false, evTie.getIsAccepted(), "tie → rejected");
    }

    @Test
    public void twoConsecutiveCycles_ballotsScopedToCycle() {
        int startIdx = alice.getCycleInfo().getCycleIndex();

        // Cycle N: one proposal, both vote accept. The helper ends in cycle N's RESULT
        // phase; cycle counter only advances once we cross into the next cycle's PROPOSAL.
        ProposalInfo n0 = singleProposalCycle("c1", "accept", "accept");

        // Cross into the next cycle's PROPOSAL phase. Now cycleIndex must have moved.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        int idxAfterFirst = alice.getCycleInfo().getCycleIndex();
        assertTrue(idxAfterFirst > startIdx, "cycle index must increment after first cycle completes");

        // Cycle N+1: previous proposal's ballot must NOT appear in this cycle's ballot list.
        boolean prevBallotStillThere = alice.getBallots().getBallotsList().stream()
                .anyMatch(b -> b.getProposal().getTxId().equals(n0.getTxId()));
        assertEquals(false, prevBallotStillThere, "previous cycle's ballot must not appear in this cycle");
    }

    private ProposalInfo singleProposalCycle(String name, String aliceVote, String bobVote) {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL);
        ProposalInfo p = alice.createGenericProposal(name, "https://x.test/" + name);
        dao.confirmTx(p.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        DaoTestUtils.await(() -> bob.getBallots().getBallotsList().stream()
                        .anyMatch(b -> b.getProposal().getTxId().equals(p.getTxId())),
                60_000, "bob sees ballot for " + p.getTxId());
        String aliceBvTx = voteAndPublish(alice, p.getTxId(), aliceVote, 1_000_000L);
        String bobBvTx = voteAndPublish(bob, p.getTxId(), bobVote, 1_000_000L);

        dao.confirmTx(alice, aliceBvTx);
        dao.confirmTx(bob, bobBvTx);
        dao.awaitBlindVotePropagation(alice, 2, "alice");
        dao.awaitBlindVotePropagation(bob, 2, "bob");
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.confirmAutoRevealsFor(alice);
        dao.confirmAutoRevealsFor(bob);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);
        return p;
    }

    private static String voteAndPublish(GrpcClient c, String proposalTxId, String vote, long stake) {
        c.setVote(proposalTxId, vote);
        return c.publishBlindVote(stake);
    }

    private static EvaluatedProposalInfo findIn(GetVoteResultsReply reply, String txId) {
        List<EvaluatedProposalInfo> matches = new ArrayList<>();
        for (EvaluatedProposalInfo ep : reply.getEvaluatedProposalsList()) {
            if (ep.getProposal().getTxId().equals(txId)) matches.add(ep);
        }
        if (matches.isEmpty()) throw new AssertionError("no evaluated proposal for tx " + txId);
        return matches.get(0);
    }
}

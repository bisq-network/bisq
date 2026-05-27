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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for DAO-state consensus across a full governance cycle.
 *
 * <p>{@code getDaoStatus()} returns {@code daoFacade.isDaoStateReadyAndInSync()}, which is
 * false while a daemon's {@code DaoStateMonitoringService} reports a block-hash conflict
 * with the seed node. The seed node has no gRPC port, so a true result on Alice (and Bob)
 * is exactly the assertion "this daemon's parsed DAO state agrees with the seed node".
 *
 * <p>Why this matters: a daemon whose DAO state diverges from the seed node fails that
 * check, and {@code OpenOfferManager.handleOfferAvailabilityRequest} then answers a take
 * with only a NACK (no OfferAvailabilityResponse) — the failure mode that flaked
 * {@link TradeScenarioTest}. Several existing DAO tests' comments also note that polluting
 * the DAO state "produces an isInConflictWithSeedNode state that breaks every subsequent
 * test's awaitDaoStateReady". This test makes that property explicit: a correctly gated
 * proposal + blind-vote + reveal + result cycle must leave every daemon in sync at each
 * milestone, not just produce the right vote outcome.
 *
 * <p>Runs on a freshly reset stack (@Tag("freshstack")) so the assertion is about this one
 * cycle, not state accumulated by the shared-stack governance suite.
 */
@Tag("freshstack")
public class DaoStaysInSyncTest extends DaoTestBase {

    // dao-setup seeds Alice with 1,000,000 BSQ. Wait for the parser to surface it before
    // spending it on a proposal — on a freshly booted stack the BSQ parser and bitcoinj
    // wallet are still catching up, and creating a governance tx too early races them.
    private static final long ALICE_BSQ_BASELINE = 1_000_000L;

    @Test
    public void daoStaysInSyncThroughProposalAndVoteCycle() {
        // Warm-up: both daemons in sync AND Alice's genesis BSQ confirmed/spendable before
        // any governance tx, so proposal creation doesn't race cold-start wallet readiness.
        assertInSync("baseline (fresh stack)");
        DaoTestUtils.await(
                () -> alice.getBalances().getBsq().getAvailableConfirmedBalance() >= ALICE_BSQ_BASELINE,
                60_000, "alice's genesis BSQ confirmed and spendable");

        // PROPOSAL: reserve >=3 blocks of headroom so creating + mining the proposal stays
        // inside the phase (matches FullCycleScenarioTest); a proposal tx mined outside the
        // PROPOSAL phase is discarded by the parser.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL, 3);
        ProposalInfo proposal = alice.createGenericProposal("sync-check", "https://x.test/sync");
        dao.confirmTx(proposal.getTxId());

        // BLIND_VOTE: gate on bob seeing the ballot — the deterministic signal that the
        // proposal payload propagated to the peer (mirrors FullCycleScenarioTest).
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        DaoTestUtils.await(() -> hasBallot(bob, proposal.getTxId()), 60_000,
                "bob sees ballot for proposal");
        assertInSync("after proposal mined + propagated to peer");
        String aliceBlindVoteTx = voteAndPublish(alice, proposal.getTxId(), "accept");
        String bobBlindVoteTx = voteAndPublish(bob, proposal.getTxId(), "accept");
        dao.confirmTx(alice, aliceBlindVoteTx);
        dao.confirmTx(bob, bobBlindVoteTx);
        dao.awaitBlindVotePropagation(alice, 2, "alice");
        dao.awaitBlindVotePropagation(bob, 2, "bob");
        assertInSync("after blind votes confirmed + propagated");

        // VOTE_REVEAL: each node auto-broadcasts its reveal tx; confirm both.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.confirmAutoRevealsFor(alice);
        dao.confirmAutoRevealsFor(bob);
        assertInSync("after vote reveal");

        // RESULT: the proposal both accepted must be accepted, and both nodes must still
        // agree with the seed node now that the cycle's result is parsed into DAO state.
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);
        EvaluatedProposalInfo evaluated = findEvaluated(alice.getVoteResults(-1), proposal.getTxId());
        assertTrue(evaluated.getIsAccepted(),
                "proposal both nodes voted accept on must be accepted");
        assertInSync("after vote result parsed");
    }

    /** Block until both daemons report being in sync with the seed node, or fail. */
    private void assertInSync(String stage) {
        DaoTestUtils.await(alice::getDaoStatus, 30_000,
                "alice DAO state in sync with seed node — " + stage);
        DaoTestUtils.await(bob::getDaoStatus, 30_000,
                "bob DAO state in sync with seed node — " + stage);
    }

    private static boolean hasBallot(GrpcClient c, String proposalTxId) {
        return c.getBallots().getBallotsList().stream()
                .anyMatch(b -> b.getProposal().getTxId().equals(proposalTxId));
    }

    private static String voteAndPublish(GrpcClient c, String proposalTxId, String vote) {
        c.setVote(proposalTxId, vote);
        return c.publishBlindVote(1_000_000L);
    }

    private static EvaluatedProposalInfo findEvaluated(GetVoteResultsReply reply, String txId) {
        for (EvaluatedProposalInfo ep : reply.getEvaluatedProposalsList()) {
            if (ep.getProposal().getTxId().equals(txId)) return ep;
        }
        throw new AssertionError("no evaluated proposal for tx " + txId);
    }
}

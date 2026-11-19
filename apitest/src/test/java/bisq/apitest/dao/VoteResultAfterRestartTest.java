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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh-stack test for dao-vote-result-restart-parse-agrees. #8105 and #8143 changed how a
 * proposal without counted votes is evaluated. The risk is a node that parses the RESULT
 * block at startup or in a resync and gets another result than the nodes that parsed it
 * live, which splits the DAO state.
 *
 * <p>One cycle with an all-ignore proposal and an accepted control proposal. Bob is then
 * restarted twice: once with his data, so he parses again from his last snapshot, which was
 * taken before the RESULT block; once without his DAO data, so he parses from genesis again
 * (blocks from bitcoind, proposals and blind votes from the seed node). Each time his vote
 * results must equal Alice's, and both must agree with the seed node ({@code getDaoStatus}).
 *
 * <p>A daemon that parses at startup takes the seed node's block hashes instead of its own,
 * so a split shows only at the next block parsed live. Hence a block is mined after each
 * restart and the status must stay true for a while.
 */
@Tag("freshstack")
public class VoteResultAfterRestartTest extends DaoTestBase {

    // container_name of bob in apitest/docker/dao-compose.yml
    private static final String BOB_CONTAINER = "bob";
    // APP_DATA_DIR/APP_NAME of bob in dao-compose.yml, then the regtest storage dir.
    private static final String BOB_DB_DIR = "/bisq/data/bisq-BTC_REGTEST_Bob/btc_regtest/db";
    // The files DaoStateStorageService.removeAndBackupAllDaoData removes.
    private static final List<String> DAO_DATA = List.of("DaoStateStore", "BlindVoteStore",
            "ProposalStore", "BallotList", "UnconfirmedBsqChangeOutputList", "TempProposalStore",
            "BurningManAccountingStore_v3", "BsqBlocks");
    private static final long ALICE_BSQ_BASELINE = 1_000_000L;
    private static final long STAY_IN_SYNC_MS = 15_000;

    @Test
    public void voteResultAgreesAfterRestartAndResync() {
        assertInSync("baseline (fresh stack)");
        DaoTestUtils.await(
                () -> alice.getBalances().getBsq().getAvailableConfirmedBalance() >= ALICE_BSQ_BASELINE,
                60_000, "alice's genesis BSQ confirmed and spendable");

        String controlTxId = runCycleWithIgnoredProposal();
        GetVoteResultsReply expected = alice.getVoteResults(-1);
        EvaluatedProposalInfo control = expected.getEvaluatedProposalsList().stream()
                .filter(e -> e.getProposal().getTxId().equals(controlTxId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("control proposal not evaluated"));
        assertTrue(control.getIsAccepted(), "control proposal must be accepted");
        assertEquals(2, control.getNumAcceptedVotes(), "both votes on the control proposal must count");
        assertBobAgrees(expected, "live parse");

        DaoTestUtils.stopContainer(BOB_CONTAINER);
        try {
            dao.generateBlocksSeenBy(2, alice, "alice");
        } finally {
            DaoTestUtils.startContainer(BOB_CONTAINER);
        }
        dao.awaitRestartedAtTip(bob, "bob");
        assertBobAgrees(expected, "restart with data");

        DaoTestUtils.stopContainer(BOB_CONTAINER);
        try {
            removeBobsDaoData();
            dao.generateBlocksSeenBy(2, alice, "alice");
        } finally {
            DaoTestUtils.startContainer(BOB_CONTAINER);
        }
        dao.awaitRestartedAtTip(bob, "bob");
        assertBobAgrees(expected, "restart without DAO data");
    }

    /** Same flow as VoteEdgeCasesTest.allIgnoreVotes_proposalRejected. Returns the control tx id. */
    private String runCycleWithIgnoredProposal() {
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL, 2);
        ProposalInfo ignored = alice.createGenericProposal("restart-ignored", "https://x.test/ri");
        dao.confirmTx(ignored.getTxId());
        ProposalInfo control = alice.createGenericProposal("restart-control", "https://x.test/rc");
        dao.confirmTx(control.getTxId());

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE);
        DaoTestUtils.await(() -> bob.getBallots().getBallotsList().stream()
                        .filter(b -> Set.of(ignored.getTxId(), control.getTxId())
                                .contains(b.getProposal().getTxId()))
                        .count() == 2,
                60_000, "bob sees both ballots");
        alice.setVote(ignored.getTxId(), "ignore");
        bob.setVote(ignored.getTxId(), "ignore");
        alice.setVote(control.getTxId(), "accept");
        bob.setVote(control.getTxId(), "accept");
        String aliceBlindVote = alice.publishBlindVote(1_000_000L);
        String bobBlindVote = bob.publishBlindVote(1_000_000L);
        dao.confirmTx(alice, aliceBlindVote);
        dao.confirmTx(bob, bobBlindVote);
        dao.awaitBlindVotePropagation(alice, 2, "alice");
        dao.awaitBlindVotePropagation(bob, 2, "bob");

        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL);
        dao.confirmAutoRevealsForAll(alice, bob);
        dao.advanceToPhase(DaoPhaseEnum.DAO_PHASE_RESULT);
        dao.generateBlocks(1);
        return control.getTxId();
    }

    /** Deletes bob's DAO data from his volume while his container is stopped. */
    private static void removeBobsDaoData() {
        String image = DaoTestUtils.runDocker(List.of("inspect", "--format", "{{.Config.Image}}",
                BOB_CONTAINER)).trim();
        // Fails if the DAO state store is missing, so a wrong path cannot pass unnoticed.
        String script = "set -e; cd " + BOB_DB_DIR + "; test -f DaoStateStore; rm -rf "
                + String.join(" ", DAO_DATA);
        DaoTestUtils.runDocker(List.of("run", "--rm", "--network", "none", "--volumes-from", BOB_CONTAINER,
                "--entrypoint", "sh", image, "-c", script));
    }

    private void assertBobAgrees(GetVoteResultsReply expected, String stage) {
        dao.generateBlocks(1);
        assertInSync(stage);
        assertStaysInSync(stage);
        DaoTestUtils.await(() -> expected.equals(bob.getVoteResults(-1)), 60_000,
                "bob's vote results equal alice's after " + stage);
        assertEquals(expected, alice.getVoteResults(-1), "alice's vote results changed after " + stage);
    }

    /** getDaoStatus is true until a differing seed hash arrived, so watch it for a while. */
    private void assertStaysInSync(String stage) {
        long end = System.currentTimeMillis() + STAY_IN_SYNC_MS;
        while (System.currentTimeMillis() < end) {
            assertTrue(alice.getDaoStatus(), "alice out of sync with seed node after " + stage);
            assertTrue(bob.getDaoStatus(), "bob out of sync with seed node after " + stage);
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void assertInSync(String stage) {
        DaoTestUtils.await(alice::getDaoStatus, 60_000,
                "alice DAO state in sync with seed node after " + stage);
        DaoTestUtils.await(bob::getDaoStatus, 60_000,
                "bob DAO state in sync with seed node after " + stage);
    }
}

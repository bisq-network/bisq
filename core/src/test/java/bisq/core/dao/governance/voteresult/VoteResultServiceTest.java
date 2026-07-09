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

import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.IssuanceProposal;
import bisq.core.dao.governance.proposal.ProposalListPresentation;
import bisq.core.dao.governance.voteresult.issuance.IssuanceService;
import bisq.core.dao.governance.votereveal.VoteRevealConsensus;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.MeritList;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.Vote;

import org.bitcoinj.core.Coin;

import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class VoteResultServiceTest {
    private static final int PRE_ACTIVATION_HEIGHT = 954_199;
    private static final int ACTIVATION_HEIGHT = 954_200;
    private static final String BLIND_VOTE_TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String VOTE_REVEAL_TX_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final String PROPOSAL_TX_ID = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
    private static final byte[] HASH_OF_BLIND_VOTE_LIST = new byte[]{0x01, 0x02, 0x03};

    @Test
    void getCombinedStakeAddsStakeAndMerit() {
        assertEquals(15, VoteResultService.getCombinedStake(10, 5, ACTIVATION_HEIGHT));
    }

    @Test
    void getCombinedStakeUsesLegacyOverflowBeforeActivation() {
        assertEquals(Long.MIN_VALUE, VoteResultService.getCombinedStake(Long.MAX_VALUE, 1, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void getCombinedStakeThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> VoteResultService.getCombinedStake(Long.MAX_VALUE, 1, ACTIVATION_HEIGHT));
    }

    @Test
    void addVoteStakeUsesLegacyOverflowBeforeActivation() {
        assertEquals(Long.MIN_VALUE, VoteResultService.addVoteStake(Long.MAX_VALUE, 1, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void addVoteStakeThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> VoteResultService.addVoteStake(Long.MAX_VALUE, 1, ACTIVATION_HEIGHT));
    }

    @Test
    void addBlindVoteListStakeUsesLegacyOverflowBeforeActivation() {
        assertEquals(Long.MIN_VALUE, VoteResultService.addBlindVoteListStake(Long.MAX_VALUE,
                1,
                PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void addBlindVoteListStakeThrowsOnOverflowAtActivation() {
        assertThrows(ArithmeticException.class,
                () -> VoteResultService.addBlindVoteListStake(Long.MAX_VALUE, 1, ACTIVATION_HEIGHT));
    }

    @Test
    void getSumIssuanceUsesLegacyOverflowBeforeActivation() {
        Set<EvaluatedProposal> evaluatedProposals = Set.of(
                acceptedIssuanceProposal(Long.MAX_VALUE),
                acceptedIssuanceProposal(1));

        assertEquals(Long.MIN_VALUE, VoteResultService.getSumIssuance(evaluatedProposals, PRE_ACTIVATION_HEIGHT));
    }

    @Test
    void getSumIssuanceThrowsOnOverflowAtActivation() {
        Set<EvaluatedProposal> evaluatedProposals = Set.of(
                acceptedIssuanceProposal(Long.MAX_VALUE),
                acceptedIssuanceProposal(1));

        assertThrows(ArithmeticException.class,
                () -> VoteResultService.getSumIssuance(evaluatedProposals, ACTIVATION_HEIGHT));
    }

    @Test
    void getDecryptedBallotsWithMeritsMatchingBlindVoteListDecryptsExactMajorityPayload() throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposalAndAddTxId(PROPOSAL_TX_ID);
        BlindVote honestBlindVote = blindVote(secretKey, true);
        DecryptedBallotsWithMerits forgedFirstDecryptedBallotsWithMerits =
                decryptedBallotsWithMerits(proposal, false);
        VoteResultService voteResultService = voteResultService(secretKey, proposal);

        Set<DecryptedBallotsWithMerits> result = voteResultService.getDecryptedBallotsWithMeritsMatchingBlindVoteList(
                Set.of(forgedFirstDecryptedBallotsWithMerits),
                List.of(honestBlindVote),
                mock(Cycle.class));

        assertEquals(1, result.size());
        Vote vote = result.iterator().next().getVote(PROPOSAL_TX_ID).orElseThrow();
        assertTrue(vote.isAccepted());
    }

    private static EvaluatedProposal acceptedIssuanceProposal(long requestedBsq) {
        Proposal proposal = mock(Proposal.class, withSettings().extraInterfaces(IssuanceProposal.class));
        when(((IssuanceProposal) proposal).getRequestedBsq()).thenReturn(Coin.valueOf(requestedBsq));
        return new EvaluatedProposal(true, new ProposalVoteResult(proposal, 0, 0, 0, 0, 0));
    }

    private static VoteResultService voteResultService(SecretKey secretKey, Proposal proposal) throws Exception {
        TxOutput voteRevealTxOutput = voteRevealTxOutput(secretKey);
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of(voteRevealTxOutput));

        BallotListService ballotListService = mock(BallotListService.class);
        when(ballotListService.getValidBallotsOfCycle()).thenReturn(List.of(new Ballot(proposal)));

        return new VoteResultService(mock(ProposalListPresentation.class),
                daoStateService,
                mock(PeriodService.class),
                ballotListService,
                mock(BlindVoteListService.class),
                mock(IssuanceService.class),
                mock(MissingDataRequestService.class));
    }

    private static TxOutput voteRevealTxOutput(SecretKey secretKey) throws Exception {
        TxOutput voteRevealTxOutput = mock(TxOutput.class);
        when(voteRevealTxOutput.getTxId()).thenReturn(VOTE_REVEAL_TX_ID);
        when(voteRevealTxOutput.getOpReturnData()).thenReturn(VoteRevealConsensus.getOpReturnData(new byte[20],
                secretKey));
        return voteRevealTxOutput;
    }

    private static DecryptedBallotsWithMerits decryptedBallotsWithMerits(Proposal proposal, boolean accepted) {
        BallotList ballotList = new BallotList(List.of(new Ballot(proposal, new Vote(accepted))));
        return new DecryptedBallotsWithMerits(HASH_OF_BLIND_VOTE_LIST,
                BLIND_VOTE_TX_ID,
                VOTE_REVEAL_TX_ID,
                123_456,
                ballotList,
                new MeritList(new ArrayList<>()));
    }

    private static BlindVote blindVote(SecretKey secretKey, boolean accepted) throws Exception {
        byte[] encryptedVotes = BlindVoteConsensus.getEncryptedVotes(voteWithProposalTxIdListBytes(accepted),
                secretKey);
        byte[] encryptedMeritList = BlindVoteConsensus.getEncryptedMeritList(protobuf.MeritList.newBuilder()
                        .build()
                        .toByteArray(),
                secretKey);
        return new BlindVote(encryptedVotes,
                BLIND_VOTE_TX_ID,
                123_456,
                encryptedMeritList,
                1_700_000_000_000L);
    }

    private static byte[] voteWithProposalTxIdListBytes(boolean accepted) {
        return protobuf.VoteWithProposalTxIdList.newBuilder()
                .addItem(protobuf.VoteWithProposalTxId.newBuilder()
                        .setProposalTxId(PROPOSAL_TX_ID)
                        .setVote(protobuf.Vote.newBuilder()
                                .setAccepted(accepted)))
                .build()
                .toByteArray();
    }
}

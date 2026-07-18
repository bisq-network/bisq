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
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.MeritList;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.Vote;

import org.bitcoinj.core.Coin;

import com.google.common.collect.ImmutableList;

import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                .cloneProposal(PROPOSAL_TX_ID);
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

    @Test
    void getDecryptedBallotsWithMeritsMatchingBlindVoteListDropsOrphanDecryptedBallots() throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        DecryptedBallotsWithMerits orphanDecryptedBallotsWithMerits = decryptedBallotsWithMerits(proposal, true);
        VoteResultService voteResultService = voteResultService(secretKey, proposal);

        Set<DecryptedBallotsWithMerits> result = voteResultService.getDecryptedBallotsWithMeritsMatchingBlindVoteList(
                Set.of(orphanDecryptedBallotsWithMerits),
                List.of(),
                mock(Cycle.class));

        assertTrue(result.isEmpty());
        assertTrue(voteResultService.getInvalidDecryptedBallotsWithMeritItems()
                .contains(orphanDecryptedBallotsWithMerits));
    }

    @Test
    void getDecryptedBallotsWithMeritsMatchingBlindVoteListDropsMissingVoteRevealData() throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        BlindVote blindVote = blindVote(secretKey, true);
        DecryptedBallotsWithMerits decryptedBallotsWithMerits = decryptedBallotsWithMerits(proposal, true);
        VoteResultService voteResultService = voteResultService(Set.of(), proposal);

        Set<DecryptedBallotsWithMerits> result = voteResultService.getDecryptedBallotsWithMeritsMatchingBlindVoteList(
                Set.of(decryptedBallotsWithMerits),
                List.of(blindVote),
                mock(Cycle.class));

        assertTrue(result.isEmpty());
        assertTrue(voteResultService.getInvalidDecryptedBallotsWithMeritItems()
                .contains(decryptedBallotsWithMerits));
    }

    @Test
    void getVoteRevealDataSetUsesOnlyBlockchainData() throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        BlindVoteListService blindVoteListService = mock(BlindVoteListService.class);
        configureVoteRevealBlockchainData(daoStateService, periodService, secretKey);
        VoteResultService voteResultService = voteResultService(daoStateService,
                periodService,
                blindVoteListService,
                proposal);

        Set<DecryptedBallotsWithMerits> result = voteResultService.getVoteRevealDataSet(ACTIVATION_HEIGHT);

        assertEquals(1, result.size());
        DecryptedBallotsWithMerits voteRevealData = result.iterator().next();
        assertEquals(123_456, voteRevealData.getStake());
        assertTrue(voteRevealData.getBallotList().isEmpty());
        assertTrue(voteRevealData.getMeritList().getList().isEmpty());
        verifyNoInteractions(blindVoteListService);
    }

    @Test
    void getDecryptedBallotsWithMeritsMatchingBlindVoteListKeepsBallotAndStakeWhenMeritIsMalformed()
            throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        BlindVote malformedBlindVote = blindVoteWithMalformedMerit(secretKey, false);
        DecryptedBallotsWithMerits voteRevealData = decryptedBallotsWithMerits(proposal, false);
        VoteResultService voteResultService = voteResultService(secretKey, proposal);

        Set<DecryptedBallotsWithMerits> result = voteResultService.getDecryptedBallotsWithMeritsMatchingBlindVoteList(
                Set.of(voteRevealData),
                List.of(malformedBlindVote),
                mock(Cycle.class));

        assertEquals(1, result.size());
        DecryptedBallotsWithMerits decryptedBallotsWithMerits = result.iterator().next();
        Vote vote = decryptedBallotsWithMerits.getVote(PROPOSAL_TX_ID).orElseThrow();
        assertFalse(vote.isAccepted());
        assertEquals(123_456, decryptedBallotsWithMerits.getStake());
        assertTrue(decryptedBallotsWithMerits.getMeritList().getList().isEmpty());
    }

    @Test
    void getMeritDecryptableBlindVoteListDropsPayloadWithMalformedMerit() throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        BlindVote malformedBlindVote = blindVoteWithMalformedMerit(secretKey, true);
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        BlindVoteListService blindVoteListService = mock(BlindVoteListService.class);
        when(blindVoteListService.getBlindVotesInPhaseAndCycle()).thenReturn(List.of(malformedBlindVote));
        VoteResultService voteResultService = voteResultService(daoStateService,
                periodService,
                blindVoteListService,
                proposal);

        Map<String, byte[]> voteRevealOpReturnDataByBlindVoteTxId = Map.of(
                BLIND_VOTE_TX_ID,
                VoteRevealConsensus.getOpReturnData(new byte[20], secretKey));

        List<BlindVote> result =
                voteResultService.getMeritDecryptableBlindVoteList(voteRevealOpReturnDataByBlindVoteTxId);

        assertTrue(result.isEmpty());
    }

    @Test
    void getMeritDecryptableBlindVoteListKeepsHonestPayload() throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        BlindVote honestBlindVote = blindVote(secretKey, true);
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        BlindVoteListService blindVoteListService = mock(BlindVoteListService.class);
        when(blindVoteListService.getBlindVotesInPhaseAndCycle()).thenReturn(List.of(honestBlindVote));
        VoteResultService voteResultService = voteResultService(daoStateService,
                periodService,
                blindVoteListService,
                proposal);

        Map<String, byte[]> voteRevealOpReturnDataByBlindVoteTxId = Map.of(
                BLIND_VOTE_TX_ID,
                VoteRevealConsensus.getOpReturnData(new byte[20], secretKey));

        List<BlindVote> result =
                voteResultService.getMeritDecryptableBlindVoteList(voteRevealOpReturnDataByBlindVoteTxId);

        assertEquals(List.of(honestBlindVote), result);
    }

    @Test
    void getMeritDecryptableBlindVoteListKeepsPayloadWithoutObservedVoteReveal() throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        // The payload has malformed merit, but since no vote reveal is on-chain we cannot yet judge it.
        // It must be kept because it may still be needed to reconstruct the majority hash.
        BlindVote malformedBlindVote = blindVoteWithMalformedMerit(secretKey, true);
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        BlindVoteListService blindVoteListService = mock(BlindVoteListService.class);
        when(blindVoteListService.getBlindVotesInPhaseAndCycle()).thenReturn(List.of(malformedBlindVote));
        VoteResultService voteResultService = voteResultService(daoStateService,
                periodService,
                blindVoteListService,
                proposal);

        List<BlindVote> result = voteResultService.getMeritDecryptableBlindVoteList(Map.of());

        assertEquals(List.of(malformedBlindVote), result);
    }

    @Test
    void malformedMeritDuplicateDoesNotPreventVoteResultCalculation() throws Exception {
        // Regression test for report19: attacker delivers a same-txid forged blind vote payload with a
        // malformed encryptedMeritList alongside the honest payload. The pre-filter must drop the forged
        // payload so the honest one is picked for majority hash matching and vote result calculation.
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        BlindVote honestBlindVote = blindVote(secretKey, false);
        BlindVote forgedBlindVote = blindVoteWithMalformedMerit(secretKey, false);
        byte[] majorityBlindVoteListHash = VoteRevealConsensus.getHashOfBlindVoteList(List.of(honestBlindVote));

        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        BlindVoteListService blindVoteListService = mock(BlindVoteListService.class);
        MissingDataRequestService missingDataRequestService = mock(MissingDataRequestService.class);
        when(blindVoteListService.getBlindVotesInPhaseAndCycle())
                .thenReturn(List.of(forgedBlindVote, honestBlindVote));
        configureVoteRevealBlockchainData(daoStateService,
                periodService,
                secretKey,
                majorityBlindVoteListHash);
        when(periodService.getFirstBlockOfPhase(ACTIVATION_HEIGHT, DaoPhase.Phase.RESULT))
                .thenReturn(ACTIVATION_HEIGHT);
        VoteResultService voteResultService = voteResultService(daoStateService,
                periodService,
                blindVoteListService,
                missingDataRequestService,
                proposal);
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(ACTIVATION_HEIGHT);

        voteResultService.onParseBlockComplete(block);

        // The forged payload was filtered out, majority hash matched, and the honest payload was decrypted
        // into a valid DecryptedBallotsWithMerits set. No republish request was needed. We do not verify
        // downstream steps (addEvaluatedProposalSet, proposal evaluation) because they require more mocking
        // of param values unrelated to the reported issue.
        verify(daoStateService).addDecryptedBallotsWithMeritsSet(anySet());
        verifyNoInteractions(missingDataRequestService);
    }

    @Test
    void requestsRepublishWhenMajorityBlindVoteListCannotBeReconstructed() throws Exception {
        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(PROPOSAL_TX_ID);
        BlindVote missingBlindVote = blindVote(secretKey, false);
        byte[] majorityBlindVoteListHash = VoteRevealConsensus.getHashOfBlindVoteList(List.of(missingBlindVote));
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        BlindVoteListService blindVoteListService = mock(BlindVoteListService.class);
        MissingDataRequestService missingDataRequestService = mock(MissingDataRequestService.class);
        when(blindVoteListService.getBlindVotesInPhaseAndCycle()).thenReturn(List.of());
        configureVoteRevealBlockchainData(daoStateService,
                periodService,
                secretKey,
                majorityBlindVoteListHash);
        when(periodService.getFirstBlockOfPhase(ACTIVATION_HEIGHT, DaoPhase.Phase.RESULT))
                .thenReturn(ACTIVATION_HEIGHT);
        VoteResultService voteResultService = voteResultService(daoStateService,
                periodService,
                blindVoteListService,
                missingDataRequestService,
                proposal);
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(ACTIVATION_HEIGHT);

        voteResultService.onParseBlockComplete(block);

        verify(missingDataRequestService).sendRepublishRequest();
    }

    private static EvaluatedProposal acceptedIssuanceProposal(long requestedBsq) {
        Proposal proposal = mock(Proposal.class, withSettings().extraInterfaces(IssuanceProposal.class));
        when(((IssuanceProposal) proposal).getRequestedBsq()).thenReturn(Coin.valueOf(requestedBsq));
        return new EvaluatedProposal(true, new ProposalVoteResult(proposal, 0, 0, 0, 0, 0));
    }

    private static VoteResultService voteResultService(SecretKey secretKey, Proposal proposal) throws Exception {
        return voteResultService(Set.of(voteRevealTxOutput(secretKey)), proposal);
    }

    private static VoteResultService voteResultService(Set<TxOutput> voteRevealTxOutputs, Proposal proposal) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(voteRevealTxOutputs);

        return voteResultService(daoStateService,
                mock(PeriodService.class),
                mock(BlindVoteListService.class),
                mock(MissingDataRequestService.class),
                proposal);
    }

    private static VoteResultService voteResultService(DaoStateService daoStateService,
                                                       PeriodService periodService,
                                                       BlindVoteListService blindVoteListService,
                                                       Proposal proposal) {
        return voteResultService(daoStateService,
                periodService,
                blindVoteListService,
                mock(MissingDataRequestService.class),
                proposal);
    }

    private static VoteResultService voteResultService(DaoStateService daoStateService,
                                                       PeriodService periodService,
                                                       BlindVoteListService blindVoteListService,
                                                       MissingDataRequestService missingDataRequestService,
                                                       Proposal proposal) {

        BallotListService ballotListService = mock(BallotListService.class);
        when(ballotListService.getValidBallotsOfCycle()).thenReturn(List.of(new Ballot(proposal)));

        return new VoteResultService(mock(ProposalListPresentation.class),
                daoStateService,
                periodService,
                ballotListService,
                blindVoteListService,
                mock(IssuanceService.class),
                missingDataRequestService);
    }

    private static void configureVoteRevealBlockchainData(DaoStateService daoStateService,
                                                          PeriodService periodService,
                                                          SecretKey secretKey) throws Exception {
        configureVoteRevealBlockchainData(daoStateService, periodService, secretKey, new byte[20]);
    }

    private static void configureVoteRevealBlockchainData(DaoStateService daoStateService,
                                                          PeriodService periodService,
                                                          SecretKey secretKey,
                                                          byte[] hashOfBlindVoteList) throws Exception {
        TxOutput voteRevealTxOutput = voteRevealTxOutput(secretKey, hashOfBlindVoteList);
        when(daoStateService.getVoteRevealOpReturnTxOutputs()).thenReturn(Set.of(voteRevealTxOutput));
        when(periodService.isTxInCorrectCycle(VOTE_REVEAL_TX_ID, ACTIVATION_HEIGHT)).thenReturn(true);
        when(periodService.isTxInPhase(VOTE_REVEAL_TX_ID, DaoPhase.Phase.VOTE_REVEAL)).thenReturn(true);
        when(periodService.getCurrentCycle()).thenReturn(mock(Cycle.class));

        Tx voteRevealTx = mock(Tx.class);
        TxInput stakeTxInput = mock(TxInput.class);
        when(voteRevealTx.getTxInputs()).thenReturn(ImmutableList.of(stakeTxInput));
        when(daoStateService.getTx(VOTE_REVEAL_TX_ID)).thenReturn(Optional.of(voteRevealTx));

        TxOutput blindVoteStakeOutput = mock(TxOutput.class);
        when(blindVoteStakeOutput.getTxOutputType()).thenReturn(TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT);
        when(blindVoteStakeOutput.getTxId()).thenReturn(BLIND_VOTE_TX_ID);
        when(blindVoteStakeOutput.getValue()).thenReturn(123_456L);
        when(daoStateService.getConnectedTxOutput(stakeTxInput)).thenReturn(Optional.of(blindVoteStakeOutput));
        when(periodService.isTxInPhaseAndCycle(BLIND_VOTE_TX_ID,
                DaoPhase.Phase.BLIND_VOTE,
                ACTIVATION_HEIGHT)).thenReturn(true);

        int blindVoteBlockHeight = ACTIVATION_HEIGHT - 10;
        Tx blindVoteTx = mock(Tx.class);
        when(blindVoteTx.getId()).thenReturn(BLIND_VOTE_TX_ID);
        when(blindVoteTx.getBlockHeight()).thenReturn(blindVoteBlockHeight);
        when(daoStateService.getTx(BLIND_VOTE_TX_ID)).thenReturn(Optional.of(blindVoteTx));
        when(daoStateService.getOptionalTxType(BLIND_VOTE_TX_ID)).thenReturn(Optional.of(TxType.BLIND_VOTE));
        when(periodService.isTxInCorrectCycle(blindVoteBlockHeight, ACTIVATION_HEIGHT)).thenReturn(true);
        when(periodService.isInPhase(blindVoteBlockHeight, DaoPhase.Phase.BLIND_VOTE)).thenReturn(true);
    }

    private static TxOutput voteRevealTxOutput(SecretKey secretKey) throws Exception {
        return voteRevealTxOutput(secretKey, new byte[20]);
    }

    private static TxOutput voteRevealTxOutput(SecretKey secretKey, byte[] hashOfBlindVoteList) throws Exception {
        TxOutput voteRevealTxOutput = mock(TxOutput.class);
        when(voteRevealTxOutput.getTxId()).thenReturn(VOTE_REVEAL_TX_ID);
        when(voteRevealTxOutput.getOpReturnData()).thenReturn(VoteRevealConsensus.getOpReturnData(hashOfBlindVoteList,
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

    private static BlindVote blindVoteWithMalformedMerit(SecretKey secretKey, boolean accepted) throws Exception {
        BlindVote blindVote = blindVote(secretKey, accepted);
        return new BlindVote(blindVote.getEncryptedVotes(),
                blindVote.getTxId(),
                blindVote.getStake(),
                new byte[]{0x01},
                blindVote.getDate());
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

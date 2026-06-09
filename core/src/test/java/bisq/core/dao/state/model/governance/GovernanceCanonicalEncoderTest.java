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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.state.model.governance;

import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.MyBlindVoteList;
import bisq.core.dao.governance.blindvote.VoteWithProposalTxIdList;
import bisq.core.dao.governance.merit.MeritConsensus;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.MyProposalList;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalPayload;
import bisq.core.dao.governance.voteresult.VoteResultConsensus;

import bisq.common.crypto.Sig;
import bisq.common.encoding.canonical.CanonicalEncoder;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GovernanceCanonicalEncoderTest {
    @Test
    public void daoPhaseEncodeCanonicalMatchesProtobuf() {
        protobuf.DaoPhase proto = protobuf.DaoPhase.newBuilder()
                .setPhaseOrdinal(DaoPhase.Phase.BLIND_VOTE.ordinal())
                .setDuration(123)
                .build();
        DaoPhase daoPhase = DaoPhase.fromProto(proto);

        assertArrayEquals(daoPhase.toProtoMessage().toByteArray(),
                daoPhase.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void cycleEncodeCanonicalMatchesProtobuf() {
        protobuf.Cycle proto = protobuf.Cycle.newBuilder()
                .setHeightOfFirstLock(1000)
                .addDaoPhase(protobuf.DaoPhase.newBuilder()
                        .setPhaseOrdinal(DaoPhase.Phase.PROPOSAL.ordinal())
                        .setDuration(10))
                .addDaoPhase(protobuf.DaoPhase.newBuilder()
                        .setPhaseOrdinal(DaoPhase.Phase.BLIND_VOTE.ordinal())
                        .setDuration(20))
                .build();
        Cycle cycle = Cycle.fromProto(proto);

        assertArrayEquals(cycle.toProtoMessage().toByteArray(),
                cycle.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void issuanceEncodeCanonicalMatchesProtobuf() {
        protobuf.Issuance proto = getIssuanceProto();
        Issuance issuance = Issuance.fromProto(proto);

        assertArrayEquals(issuance.toProtoMessage().toByteArray(),
                issuance.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void proposalSubclassesEncodeCanonicalMatchesProtobuf() {
        assertProposalCanonicalMatchesProtobuf(baseProposal()
                .setCompensationProposal(protobuf.CompensationProposal.newBuilder()
                        .setRequestedBsq(100_000)
                        .setBsqAddress("B1111111111111111111111111111111111"))
                .build());
        assertProposalCanonicalMatchesProtobuf(baseProposal()
                .setReimbursementProposal(protobuf.ReimbursementProposal.newBuilder()
                        .setRequestedBsq(200_000)
                        .setBsqAddress("B2222222222222222222222222222222222"))
                .build());
        assertProposalCanonicalMatchesProtobuf(baseProposal()
                .setChangeParamProposal(protobuf.ChangeParamProposal.newBuilder()
                        .setParam(Param.PROPOSAL_FEE.name())
                        .setParamValue("3"))
                .build());
        assertProposalCanonicalMatchesProtobuf(baseProposal()
                .setRoleProposal(protobuf.RoleProposal.newBuilder()
                        .setRole(getRoleProto())
                        .setRequiredBondUnit(50)
                        .setUnlockTime(1440))
                .build());
        assertProposalCanonicalMatchesProtobuf(baseProposal()
                .setConfiscateBondProposal(protobuf.ConfiscateBondProposal.newBuilder()
                        .setLockupTxId("lockup-tx"))
                .build());
        assertProposalCanonicalMatchesProtobuf(baseProposal()
                .setGenericProposal(protobuf.GenericProposal.newBuilder())
                .build());
        assertProposalCanonicalMatchesProtobuf(baseProposal()
                .setRemoveAssetProposal(protobuf.RemoveAssetProposal.newBuilder()
                        .setTickerSymbol("XYZ"))
                .build());
    }

    @Test
    public void tempProposalPayloadEncodeCanonicalMatchesStoragePayloadProtobuf() {
        TempProposalPayload tempProposalPayload = new TempProposalPayload(
                Proposal.fromProto(getCompensationProposalProto()),
                Sig.generateKeyPair().getPublic());

        assertArrayEquals(tempProposalPayload.toProtoMessage().toByteArray(),
                tempProposalPayload.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void tempProposalPayloadWithExtraDataMapEncodeCanonicalMatchesStoragePayloadProtobuf() {
        protobuf.TempProposalPayload proto = protobuf.TempProposalPayload.newBuilder()
                .setProposal(getCompensationProposalProto())
                .setOwnerPubKeyEncoded(ByteString.copyFrom(Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic())))
                .putExtraData("futureKey", "futureValue")
                .build();
        TempProposalPayload tempProposalPayload = TempProposalPayload.fromProto(proto);

        assertArrayEquals(tempProposalPayload.toProtoMessage().toByteArray(),
                tempProposalPayload.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void myProposalListEncodeCanonicalMatchesPersistableEnvelopeProtobuf() {
        MyProposalList myProposalList = new MyProposalList(List.of(Proposal.fromProto(getCompensationProposalProto())));

        assertArrayEquals(myProposalList.toProtoMessage().toByteArray(),
                myProposalList.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void blindVoteAndMyBlindVoteListEncodeCanonicalMatchesProtobuf() {
        protobuf.BlindVote proto = protobuf.BlindVote.newBuilder()
                .setEncryptedVotes(ByteString.copyFrom(new byte[]{0x01, 0x02, 0x03}))
                .setTxId("blind-vote-tx")
                .setStake(123_456)
                .setEncryptedMeritList(ByteString.copyFrom(new byte[]{0x04, 0x05}))
                .setDate(1_700_000_000_000L)
                .build();
        BlindVote blindVote = BlindVote.fromProto(proto);

        assertArrayEquals(blindVote.toProtoMessage().toByteArray(),
                blindVote.encodeCanonical(CanonicalEncoder.DEFAULT));

        MyBlindVoteList myBlindVoteList = new MyBlindVoteList(List.of(blindVote));

        assertArrayEquals(myBlindVoteList.toProtoMessage().toByteArray(),
                myBlindVoteList.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void blindVoteWithExtraDataMapEncodeCanonicalMatchesProtobuf() {
        TreeMap<String, String> extraDataMap = new TreeMap<>();
        extraDataMap.put("futureKey", "futureValue");

        BlindVote blindVote = new BlindVote(new byte[]{0x01, 0x02, 0x03},
                "blind-vote-tx",
                123_456,
                new byte[]{0x04, 0x05},
                1_700_000_000_000L,
                extraDataMap);

        assertArrayEquals(blindVote.toProtoMessage().toByteArray(),
                blindVote.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void compensationProposalWithExtraDataMapEncodeCanonicalMatchesProtobuf() {
        TreeMap<String, String> extraDataMap = new TreeMap<>();
        extraDataMap.put("futureKey", "futureValue");
        extraDataMap.put(CompensationProposal.BURNING_MAN_RECEIVER_ADDRESS, "receiverAddress");
        Proposal proposal = new CompensationProposal("proposal-name",
                "https://bisq.network/proposal",
                Coin.valueOf(100_000),
                "B1111111111111111111111111111111111",
                extraDataMap)
                .cloneProposalAndAddTxId("proposal-tx");

        assertArrayEquals(proposal.toProtoMessage().toByteArray(),
                proposal.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void roleEncodeCanonicalMatchesProtobuf() {
        Role role = Role.fromProto(getRoleProto());

        assertArrayEquals(role.toProtoMessage().toByteArray(),
                role.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void paramChangeEncodeCanonicalMatchesProtobuf() {
        protobuf.ParamChange proto = protobuf.ParamChange.newBuilder()
                .setParamName(Param.PROPOSAL_FEE.name())
                .setParamValue("3")
                .setActivationHeight(12345)
                .build();
        ParamChange paramChange = ParamChange.fromProto(proto);

        assertArrayEquals(paramChange.toProtoMessage().toByteArray(),
                paramChange.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void voteEncodeCanonicalMatchesProtobuf() {
        Vote vote = Vote.fromProto(protobuf.Vote.newBuilder()
                .setAccepted(true)
                .build());

        assertArrayEquals(vote.toProtoMessage().toByteArray(),
                vote.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void emptyVoteWithProposalTxIdListEncodeCanonicalMatchesProtobuf() throws Exception {
        assertVoteWithProposalTxIdListCanonicalMatchesProtobuf(protobuf.VoteWithProposalTxIdList.newBuilder()
                .build());
    }

    @Test
    public void voteWithProposalTxIdListWithoutVoteEncodeCanonicalMatchesProtobuf() throws Exception {
        assertVoteWithProposalTxIdListCanonicalMatchesProtobuf(protobuf.VoteWithProposalTxIdList.newBuilder()
                .addItem(voteWithProposalTxIdProto("proposal-without-vote"))
                .build());
    }

    @Test
    public void voteWithProposalTxIdListWithRejectedVoteEncodeCanonicalMatchesProtobuf() throws Exception {
        byte[] canonicalBytes = assertVoteWithProposalTxIdListCanonicalMatchesProtobuf(
                protobuf.VoteWithProposalTxIdList.newBuilder()
                        .addItem(voteWithProposalTxIdProto("proposal-rejected", false))
                        .build());

        protobuf.VoteWithProposalTxIdList parsed = protobuf.VoteWithProposalTxIdList.parseFrom(canonicalBytes);
        assertTrue(parsed.getItem(0).hasVote());
        assertFalse(parsed.getItem(0).getVote().getAccepted());
    }

    @Test
    public void voteWithProposalTxIdListWithAcceptedVoteEncodeCanonicalMatchesProtobuf() throws Exception {
        assertVoteWithProposalTxIdListCanonicalMatchesProtobuf(protobuf.VoteWithProposalTxIdList.newBuilder()
                .addItem(voteWithProposalTxIdProto("proposal-accepted", true))
                .build());
    }

    @Test
    public void voteWithProposalTxIdListEncodeCanonicalPreservesListOrder() throws Exception {
        byte[] canonicalBytes = assertVoteWithProposalTxIdListCanonicalMatchesProtobuf(
                protobuf.VoteWithProposalTxIdList.newBuilder()
                        .addItem(voteWithProposalTxIdProto("proposal-a", true))
                        .addItem(voteWithProposalTxIdProto("proposal-b"))
                        .addItem(voteWithProposalTxIdProto("proposal-c", false))
                        .build());

        protobuf.VoteWithProposalTxIdList parsed = protobuf.VoteWithProposalTxIdList.parseFrom(canonicalBytes);
        assertEquals("proposal-a", parsed.getItem(0).getProposalTxId());
        assertEquals("proposal-b", parsed.getItem(1).getProposalTxId());
        assertEquals("proposal-c", parsed.getItem(2).getProposalTxId());
    }

    @Test
    public void encryptedCanonicalVoteWithProposalTxIdListDecryptsAndParsesAsProtobuf() throws Exception {
        protobuf.VoteWithProposalTxIdList proto = protobuf.VoteWithProposalTxIdList.newBuilder()
                .addItem(voteWithProposalTxIdProto("proposal-a", true))
                .addItem(voteWithProposalTxIdProto("proposal-b", false))
                .build();
        VoteWithProposalTxIdList voteWithProposalTxIdList =
                VoteWithProposalTxIdList.getVoteWithProposalTxIdListFromBytes(proto.toByteArray());

        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        byte[] encryptedVotes = BlindVoteConsensus.getEncryptedVotes(voteWithProposalTxIdList.encodeCanonical(),
                secretKey);
        VoteWithProposalTxIdList decrypted = VoteResultConsensus.decryptVotes(encryptedVotes, secretKey);

        assertArrayEquals(proto.toByteArray(), decrypted.toProtoMessage().toByteArray());
    }

    @Test
    public void ballotAndBallotListEncodeCanonicalMatchesProtobuf() {
        protobuf.Ballot ballotProto = protobuf.Ballot.newBuilder()
                .setProposal(getCompensationProposalProto())
                .setVote(protobuf.Vote.newBuilder()
                        .setAccepted(true))
                .build();
        Ballot ballot = Ballot.fromProto(ballotProto);

        assertArrayEquals(ballot.toProtoMessage().toByteArray(),
                ballot.encodeCanonical(CanonicalEncoder.DEFAULT));

        protobuf.BallotList ballotListProto = protobuf.BallotList.newBuilder()
                .addBallot(ballotProto)
                .build();
        BallotList ballotList = BallotList.fromProto(ballotListProto);

        assertArrayEquals(ballotListProto.toByteArray(),
                ballotList.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void meritAndMeritListEncodeCanonicalMatchesProtobuf() throws Exception {
        protobuf.Merit meritProto = getMeritProto("issuance-tx", new byte[]{0x01, 0x02, 0x03});
        Merit merit = Merit.fromProto(meritProto);

        assertArrayEquals(merit.toProtoMessage().toByteArray(),
                merit.encodeCanonical(CanonicalEncoder.DEFAULT));

        assertMeritListCanonicalMatchesProtobuf(protobuf.MeritList.newBuilder()
                .addMerit(meritProto)
                .build());
    }

    @Test
    public void emptyMeritListEncodeCanonicalMatchesProtobuf() throws Exception {
        assertMeritListCanonicalMatchesProtobuf(protobuf.MeritList.newBuilder()
                .build());
    }

    @Test
    public void meritListEncodeCanonicalPreservesListOrder() throws Exception {
        byte[] canonicalBytes = assertMeritListCanonicalMatchesProtobuf(protobuf.MeritList.newBuilder()
                .addMerit(getMeritProto("issuance-a", new byte[]{0x01}))
                .addMerit(getMeritProto("issuance-b", new byte[]{}))
                .addMerit(getMeritProto("issuance-c", new byte[]{0x02, 0x03}))
                .build());

        protobuf.MeritList parsed = protobuf.MeritList.parseFrom(canonicalBytes);
        assertEquals("issuance-a", parsed.getMerit(0).getIssuance().getTxId());
        assertEquals("issuance-b", parsed.getMerit(1).getIssuance().getTxId());
        assertEquals("issuance-c", parsed.getMerit(2).getIssuance().getTxId());
    }

    @Test
    public void encryptedCanonicalMeritListDecryptsAndParsesAsProtobuf() throws Exception {
        protobuf.MeritList proto = protobuf.MeritList.newBuilder()
                .addMerit(getMeritProto("issuance-a", new byte[]{0x01}))
                .addMerit(getMeritProto("issuance-b", new byte[]{0x02, 0x03}))
                .build();
        MeritList meritList = MeritList.getMeritListFromBytes(proto.toByteArray());

        SecretKey secretKey = BlindVoteConsensus.createSecretKey();
        byte[] encryptedMeritList = BlindVoteConsensus.getEncryptedMeritList(meritList.encodeCanonical(), secretKey);
        MeritList decrypted = MeritConsensus.decryptMeritList(encryptedMeritList, secretKey);

        assertArrayEquals(proto.toByteArray(), decrypted.toProtoMessage().toByteArray());
    }

    @Test
    public void proposalVoteResultAndEvaluatedProposalEncodeCanonicalMatchesProtobuf() {
        protobuf.ProposalVoteResult proposalVoteResultProto = protobuf.ProposalVoteResult.newBuilder()
                .setProposal(getCompensationProposalProto())
                .setStakeOfAcceptedVotes(1000)
                .setStakeOfRejectedVotes(200)
                .setNumAcceptedVotes(3)
                .setNumRejectedVotes(1)
                .setNumIgnoredVotes(2)
                .build();
        ProposalVoteResult proposalVoteResult = ProposalVoteResult.fromProto(proposalVoteResultProto);

        assertArrayEquals(proposalVoteResult.toProtoMessage().toByteArray(),
                proposalVoteResult.encodeCanonical(CanonicalEncoder.DEFAULT));

        protobuf.EvaluatedProposal evaluatedProposalProto = protobuf.EvaluatedProposal.newBuilder()
                .setIsAccepted(true)
                .setProposalVoteResult(proposalVoteResultProto)
                .build();
        EvaluatedProposal evaluatedProposal = EvaluatedProposal.fromProto(evaluatedProposalProto);

        assertArrayEquals(evaluatedProposal.toProtoMessage().toByteArray(),
                evaluatedProposal.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void decryptedBallotsWithMeritsEncodeCanonicalMatchesProtobuf() {
        protobuf.Ballot ballotProto = protobuf.Ballot.newBuilder()
                .setProposal(getCompensationProposalProto())
                .setVote(protobuf.Vote.newBuilder()
                        .setAccepted(true))
                .build();
        protobuf.Merit meritProto = protobuf.Merit.newBuilder()
                .setIssuance(getIssuanceProto())
                .setSignature(ByteString.copyFrom(new byte[]{0x01, 0x02, 0x03}))
                .build();
        protobuf.DecryptedBallotsWithMerits proto = protobuf.DecryptedBallotsWithMerits.newBuilder()
                .setHashOfBlindVoteList(ByteString.copyFrom(new byte[]{0x0a, 0x0b}))
                .setBlindVoteTxId("blind-vote-tx")
                .setVoteRevealTxId("vote-reveal-tx")
                .setStake(123_456)
                .setBallotList(protobuf.BallotList.newBuilder()
                        .addAllBallot(List.of(ballotProto)))
                .setMeritList(protobuf.MeritList.newBuilder()
                        .addAllMerit(List.of(meritProto)))
                .build();
        DecryptedBallotsWithMerits decryptedBallotsWithMerits = DecryptedBallotsWithMerits.fromProto(proto);

        assertArrayEquals(decryptedBallotsWithMerits.toProtoMessage().toByteArray(),
                decryptedBallotsWithMerits.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    private static void assertProposalCanonicalMatchesProtobuf(protobuf.Proposal proto) {
        Proposal proposal = Proposal.fromProto(proto);

        assertArrayEquals(proposal.toProtoMessage().toByteArray(),
                proposal.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    private static byte[] assertVoteWithProposalTxIdListCanonicalMatchesProtobuf(protobuf.VoteWithProposalTxIdList proto)
            throws Exception {
        VoteWithProposalTxIdList voteWithProposalTxIdList =
                VoteWithProposalTxIdList.getVoteWithProposalTxIdListFromBytes(proto.toByteArray());

        byte[] canonicalBytes = voteWithProposalTxIdList.encodeCanonical(CanonicalEncoder.DEFAULT);
        assertArrayEquals(proto.toByteArray(), canonicalBytes);
        assertArrayEquals(proto.toByteArray(), protobuf.VoteWithProposalTxIdList.parseFrom(canonicalBytes).toByteArray());
        return canonicalBytes;
    }

    private static byte[] assertMeritListCanonicalMatchesProtobuf(protobuf.MeritList proto) throws Exception {
        MeritList meritList = MeritList.getMeritListFromBytes(proto.toByteArray());

        byte[] canonicalBytes = meritList.encodeCanonical(CanonicalEncoder.DEFAULT);
        assertArrayEquals(proto.toByteArray(), canonicalBytes);
        assertArrayEquals(proto.toByteArray(), protobuf.MeritList.parseFrom(canonicalBytes).toByteArray());
        return canonicalBytes;
    }

    private static protobuf.Merit getMeritProto(String issuanceTxId, byte[] signature) {
        return protobuf.Merit.newBuilder()
                .setIssuance(getIssuanceProto(issuanceTxId))
                .setSignature(ByteString.copyFrom(signature))
                .build();
    }

    private static protobuf.VoteWithProposalTxId voteWithProposalTxIdProto(String proposalTxId) {
        return protobuf.VoteWithProposalTxId.newBuilder()
                .setProposalTxId(proposalTxId)
                .build();
    }

    private static protobuf.VoteWithProposalTxId voteWithProposalTxIdProto(String proposalTxId, boolean accepted) {
        return protobuf.VoteWithProposalTxId.newBuilder()
                .setProposalTxId(proposalTxId)
                .setVote(protobuf.Vote.newBuilder()
                        .setAccepted(accepted))
                .build();
    }

    private static protobuf.Proposal getCompensationProposalProto() {
        return baseProposal()
                .setCompensationProposal(protobuf.CompensationProposal.newBuilder()
                        .setRequestedBsq(100_000)
                        .setBsqAddress("B1111111111111111111111111111111111"))
                .build();
    }

    private static protobuf.Proposal.Builder baseProposal() {
        return protobuf.Proposal.newBuilder()
                .setName("proposal-name")
                .setLink("https://bisq.network/proposal")
                .setVersion(1)
                .setCreationDate(1_700_000_000_000L)
                .setTxId("proposal-tx");
    }

    private static protobuf.Role getRoleProto() {
        return protobuf.Role.newBuilder()
                .setUid("role-uid")
                .setName("role-name")
                .setLink("https://bisq.network/roles/16")
                .setBondedRoleType(BondedRoleType.GITHUB_ADMIN.name())
                .build();
    }

    private static protobuf.Issuance getIssuanceProto() {
        return getIssuanceProto("issuance-tx");
    }

    private static protobuf.Issuance getIssuanceProto(String txId) {
        return protobuf.Issuance.newBuilder()
                .setTxId(txId)
                .setChainHeight(12345)
                .setAmount(100_000)
                .setPubKey("02abcdef")
                .setIssuanceType(IssuanceType.COMPENSATION.name())
                .build();
    }
}

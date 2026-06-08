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

import bisq.core.dao.governance.param.Param;
import bisq.common.encoding.canonical.CanonicalEncoder;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
    public void meritAndMeritListEncodeCanonicalMatchesProtobuf() {
        protobuf.Merit meritProto = protobuf.Merit.newBuilder()
                .setIssuance(getIssuanceProto())
                .setSignature(ByteString.copyFrom(new byte[]{0x01, 0x02, 0x03}))
                .build();
        Merit merit = Merit.fromProto(meritProto);

        assertArrayEquals(merit.toProtoMessage().toByteArray(),
                merit.encodeCanonical(CanonicalEncoder.DEFAULT));

        protobuf.MeritList meritListProto = protobuf.MeritList.newBuilder()
                .addMerit(meritProto)
                .build();
        MeritList meritList = MeritList.fromProto(meritListProto);

        assertArrayEquals(meritList.toProtoMessage().toByteArray(),
                meritList.encodeCanonical(CanonicalEncoder.DEFAULT));
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
        return protobuf.Issuance.newBuilder()
                .setTxId("issuance-tx")
                .setChainHeight(12345)
                .setAmount(100_000)
                .setPubKey("02abcdef")
                .setIssuanceType(IssuanceType.COMPENSATION.name())
                .build();
    }
}

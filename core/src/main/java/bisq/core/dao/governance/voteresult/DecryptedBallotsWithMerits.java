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

import bisq.core.dao.governance.ballot.Ballot;
import bisq.core.dao.governance.ballot.BallotList;
import bisq.core.dao.governance.ballot.vote.Vote;
import bisq.core.dao.governance.merit.MeritConsensus;
import bisq.core.dao.governance.merit.MeritList;
import bisq.core.dao.state.BsqStateService;

import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.util.Optional;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds all data from a decrypted vote item.
 */
@Slf4j
@Value
public class DecryptedBallotsWithMerits implements PersistablePayload {
    private final byte[] hashOfBlindVoteList;
    private final String blindVoteTxId;
    private final String voteRevealTxId;
    private final long stake;
    private final BallotList ballotList;
    private final MeritList meritList;

    public DecryptedBallotsWithMerits(byte[] hashOfBlindVoteList, String blindVoteTxId, String voteRevealTxId, long stake,
                                      BallotList ballotList, MeritList meritList) {
        this.hashOfBlindVoteList = hashOfBlindVoteList;
        this.blindVoteTxId = blindVoteTxId;
        this.voteRevealTxId = voteRevealTxId;
        this.stake = stake;
        this.ballotList = ballotList;
        this.meritList = meritList;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.DecryptedBallotsWithMerits toProtoMessage() {
        return getBuilder().build();
    }

    public PB.DecryptedBallotsWithMerits.Builder getBuilder() {
        return PB.DecryptedBallotsWithMerits.newBuilder()
                .setHashOfBlindVoteList(ByteString.copyFrom(hashOfBlindVoteList))
                .setBlindVoteTxId(blindVoteTxId)
                .setVoteRevealTxId(voteRevealTxId)
                .setStake(stake)
                .setBallotList(ballotList.getBuilder())
                .setMeritList(meritList.getBuilder());
    }

    public static DecryptedBallotsWithMerits fromProto(PB.DecryptedBallotsWithMerits proto) {
        return new DecryptedBallotsWithMerits(proto.getHashOfBlindVoteList().toByteArray(),
                proto.getBlindVoteTxId(),
                proto.getVoteRevealTxId(),
                proto.getStake(),
                BallotList.fromProto(proto.getBallotList()),
                MeritList.fromProto(proto.getMeritList()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Vote> getVote(String proposalTxId) {
        return ballotList.stream()
                .filter(ballot -> ballot.getTxId().equals(proposalTxId))
                .map(Ballot::getVote)
                .findAny();
    }

    public long getMerit(BsqStateService bsqStateService) {
        return MeritConsensus.getMeritStake(blindVoteTxId, meritList, bsqStateService);
    }

    @Override
    public String toString() {
        return "DecryptedBallotsWithMerits{" +
                "\n     hashOfBlindVoteList=" + Utilities.bytesAsHexString(hashOfBlindVoteList) +
                ",\n     blindVoteTxId='" + blindVoteTxId + '\'' +
                ",\n     voteRevealTxId='" + voteRevealTxId + '\'' +
                ",\n     stake=" + stake +
                ",\n     ballotList=" + ballotList +
                ",\n     meritList=" + meritList +
                "\n}";
    }
}

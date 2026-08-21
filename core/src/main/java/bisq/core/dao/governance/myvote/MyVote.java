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

package bisq.core.dao.governance.myvote;

import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.merit.MeritConsensus;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.MeritList;

import bisq.common.crypto.Encryption;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import javax.crypto.SecretKey;

import java.util.Date;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Holds all my vote related data. Is not immutable as revealTxId is set later. Only used for local persistence and not
 * in consensus critical operations.
 */
@EqualsAndHashCode
@Slf4j
@Getter
public class MyVote implements PersistablePayload {
    private int height;
    private final BallotList ballotList;
    private final byte[] secretKeyEncoded;
    private final BlindVote blindVote;
    private final long date;
    @Setter
    @Nullable
    private String revealTxId;

    // Used just for caching
    @JsonExclude
    private final transient SecretKey secretKey;

    MyVote(int height,
           BallotList ballotList,
           byte[] secretKeyEncoded,
           BlindVote blindVote) {
        this(height,
                ballotList,
                secretKeyEncoded,
                blindVote,
                new Date().getTime(),
                null);

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MyVote(int height,
                   BallotList ballotList,
                   byte[] secretKeyEncoded,
                   BlindVote blindVote,
                   long date,
                   @Nullable String revealTxId) {
        this.height = height;
        this.ballotList = ballotList;
        this.secretKeyEncoded = secretKeyEncoded;
        this.blindVote = blindVote;
        this.date = date;
        this.revealTxId = revealTxId;

        secretKey = Encryption.getSecretKeyFromBytes(secretKeyEncoded);
    }

    @Override
    public protobuf.MyVote toProtoMessage() {
        final protobuf.MyVote.Builder builder = protobuf.MyVote.newBuilder()
                .setHeight(height)
                .setBlindVote(blindVote.getBuilder())
                .setBallotList(ballotList.getBuilder())
                .setSecretKeyEncoded(ByteString.copyFrom(secretKeyEncoded))
                .setDate(date);
        Optional.ofNullable(revealTxId).ifPresent(builder::setRevealTxId);
        return builder.build();
    }

    public static MyVote fromProto(protobuf.MyVote proto) {
        return new MyVote(proto.getHeight(),
                BallotList.fromProto(proto.getBallotList()),
                proto.getSecretKeyEncoded().toByteArray(),
                BlindVote.fromProto(proto.getBlindVote()),
                proto.getDate(),
                proto.getRevealTxId().isEmpty() ? null : proto.getRevealTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getBlindVoteTxId() {
        return blindVote.getTxId();
    }

    public long getMerit(MyBlindVoteListService myBlindVoteListService, DaoStateService daoStateService) {
        MeritList meritList = myBlindVoteListService.getMerits(blindVote.getTxId());
        if (daoStateService.getTx(blindVote.getTxId()).isPresent())
            return MeritConsensus.getMeritStake(blindVote.getTxId(), meritList, daoStateService);
        else
            return MeritConsensus.getCurrentlyAvailableMerit(meritList, daoStateService.getChainHeight());
    }

    @Override
    public String toString() {
        return "MyVote{" +
                "\n     ballotList=" + ballotList +
                ",\n     secretKeyEncoded=" + Utilities.bytesAsHexString(secretKeyEncoded) +
                ",\n     blindVotePayload=" + blindVote +
                ",\n     date=" + new Date(date) +
                ",\n     revealTxId='" + revealTxId + '\'' +
                "\n}";
    }
}

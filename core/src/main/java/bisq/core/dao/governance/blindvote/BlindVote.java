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

package bisq.core.dao.governance.blindvote;

import bisq.core.dao.governance.ConsensusCritical;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Map;
import java.util.Optional;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.Immutable;

/**
 * Holds encryptedVotes, encryptedMeritList, txId of blindVote tx and stake.
 * A encryptedVotes for 1 proposal is 304 bytes
 */
@Immutable
@Slf4j
@Value
public final class BlindVote implements PersistablePayload, NetworkPayload, ConsensusCritical {
    private final byte[] encryptedVotes; // created from voteWithProposalTxIdList
    private final String txId;
    // Stake is revealed in the BSQ tx anyway as output value so no reason to encrypt it here.
    private final long stake;
    private byte[] encryptedMeritList;
    // Publish date of the proposal.
    // We do not use the date at the moment but we prefer to keep it here as it might be
    // used as a relevant protection tool for late publishing attacks.
    // We don't have a clear concept now how to do it but as it will be part of the opReturn data it will impossible
    // to game the publish date. Together with the block time we can use that for some checks. But as said no clear
    // concept yet...
    // As adding that field later would break consensus we prefer to add it now. In the worst case it will stay
    // an unused field.
    private final long date;

    // This hash map allows addition of data in future versions without breaking consensus
    private final Map<String, String> extraDataMap;

    public BlindVote(byte[] encryptedVotes,
                     String txId,
                     long stake,
                     byte[] encryptedMeritList,
                     long date,
                     Map<String, String> extraDataMap) {
        this.encryptedVotes = encryptedVotes;
        this.txId = txId;
        this.stake = stake;
        this.encryptedMeritList = encryptedMeritList;
        this.date = date;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Used for sending over the network
    @Override
    public protobuf.BlindVote toProtoMessage() {
        return getBuilder().build();
    }

    @NotNull
    public protobuf.BlindVote.Builder getBuilder() {
        protobuf.BlindVote.Builder builder = protobuf.BlindVote.newBuilder();
        builder.setEncryptedVotes(ByteString.copyFrom(encryptedVotes))
                .setTxId(txId)
                .setStake(stake)
                .setEncryptedMeritList(ByteString.copyFrom(encryptedMeritList))
                .setDate(date);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    public static BlindVote fromProto(protobuf.BlindVote proto) {
        return new BlindVote(proto.getEncryptedVotes().toByteArray(),
                proto.getTxId(),
                proto.getStake(),
                proto.getEncryptedMeritList().toByteArray(),
                proto.getDate(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                        null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String toString() {
        return "BlindVotePayload{" +
                "\n     encryptedVotes=" + Utilities.bytesAsHexString(encryptedVotes) +
                ",\n     txId='" + txId + '\'' +
                ",\n     stake=" + stake +
                ",\n     encryptedMeritList=" + Utilities.bytesAsHexString(encryptedMeritList) +
                ",\n     date=" + date +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}

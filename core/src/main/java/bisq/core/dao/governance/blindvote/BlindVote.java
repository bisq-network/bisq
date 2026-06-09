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

import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.encoding.canonical.TreeMapIterator;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Holds encryptedVotes, encryptedMeritList, txId of blindVote tx and stake.
 * A encryptedVotes for 1 proposal is 304 bytes
 */
@Immutable
@Slf4j
@Value
public final class BlindVote implements PersistablePayload, NetworkPayload, ConsensusCritical, Canonical {
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
    @Nullable
    private final TreeMap<String, String> extraDataMap;

    public BlindVote(byte[] encryptedVotes,
                     String txId,
                     long stake,
                     byte[] encryptedMeritList,
                     long date) {
        this(encryptedVotes,
                txId,
                stake,
                encryptedMeritList,
                date,
                new TreeMap<>());
    }

    public BlindVote(byte[] encryptedVotes,
                     String txId,
                     long stake,
                     byte[] encryptedMeritList,
                     long date,
                     @Nullable TreeMap<String, String> extraDataMap) {
        this.encryptedVotes = encryptedVotes;
        this.txId = txId;
        this.stake = stake;
        this.encryptedMeritList = encryptedMeritList;
        this.date = date;

        Map<String, String> validatedExtraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.extraDataMap = validatedExtraDataMap == null ? null : new TreeMap<>(validatedExtraDataMap);
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
                        null : new TreeMap<>(proto.getExtraDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final CanonicalSchema<BlindVote> SCHEMA = CanonicalSchema.<BlindVote>newBuilder()
            .bytes(1, BlindVote::getEncryptedVotes)
            .string(2, BlindVote::getTxId)
            .int64(3, BlindVote::getStake)
            .bytes(4, BlindVote::getEncryptedMeritList)
            .int64(5, BlindVote::getDate)
            .mapStringToString(6,
                    BlindVote::getExtraDataMapForCanonical,
                    TreeMapIterator.naturalOrder())
            .build();

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }

    private Map<String, String> getExtraDataMapForCanonical() {
        return extraDataMap == null ? Collections.emptyMap() : extraDataMap;
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

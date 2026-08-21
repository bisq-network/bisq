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

package bisq.core.dao.governance.blindvote.storage;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.governance.blindvote.BlindVote;

import bisq.network.p2p.storage.payload.InvalidPersistableNetworkPayloadException;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Arrays;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Wrapper for proposal to be stored in the append-only BlindVoteStore storage.
 *
 * Data size: 185 bytes
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
public final class BlindVotePayload implements PersistableNetworkPayload, ConsensusCritical {

    private final BlindVote blindVote;
    protected final byte[] hash;        // 20 byte

    public BlindVotePayload(BlindVote blindVote) {
        this.blindVote = blindVote;
        this.hash = Hash.getRipemd160hash(blindVote.encodeCanonical());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private protobuf.BlindVotePayload.Builder getBlindVoteBuilder() {
        return protobuf.BlindVotePayload.newBuilder()
                .setBlindVote(blindVote.toProtoMessage())
                .setHash(ByteString.copyFrom(hash));
    }

    @Override
    public protobuf.PersistableNetworkPayload toProtoMessage() {
        return protobuf.PersistableNetworkPayload.newBuilder().setBlindVotePayload(getBlindVoteBuilder()).build();
    }

    public protobuf.BlindVotePayload toProtoBlindVotePayload() {
        return getBlindVoteBuilder().build();
    }


    public static BlindVotePayload fromProto(protobuf.BlindVotePayload proto) {
        BlindVote blindVote = BlindVote.fromProto(proto.getBlindVote());
        BlindVotePayload blindVotePayload = new BlindVotePayload(blindVote);

        byte[] hashFromProto = proto.getHash().toByteArray();
        if (!Arrays.equals(hashFromProto, blindVotePayload.getHash())) {
            throw new InvalidPersistableNetworkPayloadException("BlindVotePayload hash field does not match blind vote data. " +
                    "blindVoteTxId=" + blindVote.getTxId() +
                    ", hashFromProto=" + Utilities.bytesAsHexString(hashFromProto) +
                    ", computedHash=" + Utilities.bytesAsHexString(blindVotePayload.getHash()));
        }

        return blindVotePayload;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistableNetworkPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean verifyHashSize() {
        return hash.length == 20;
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public String toString() {
        return "BlindVotePayload{" +
                "\n     blindVote=" + blindVote +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}

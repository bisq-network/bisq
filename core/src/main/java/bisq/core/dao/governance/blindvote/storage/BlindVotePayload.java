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

import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

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
        this(blindVote, Hash.getRipemd160hash(blindVote.toProtoMessage().toByteArray()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BlindVotePayload(BlindVote blindVote, byte[] hash) {
        this.blindVote = blindVote;
        this.hash = hash;
    }

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
        return new BlindVotePayload(BlindVote.fromProto(proto.getBlindVote()),
                proto.getHash().toByteArray());
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

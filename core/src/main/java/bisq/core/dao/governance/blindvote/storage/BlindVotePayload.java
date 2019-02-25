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

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.DateTolerantPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.Hash;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.util.Date;
import java.util.concurrent.TimeUnit;

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
public final class BlindVotePayload implements PersistableNetworkPayload, PersistableEnvelope, DateTolerantPayload,
        CapabilityRequiringPayload, ConsensusCritical {
    private static final long TOLERANCE = TimeUnit.HOURS.toMillis(5); // +/- 5 hours

    private final BlindVote blindVote;
    private final long date;            // 8 byte
    protected final byte[] hash;        // 20 byte

    public BlindVotePayload(BlindVote blindVote) {
        this(blindVote,
                new Date().getTime(),
                Hash.getRipemd160hash(blindVote.toProtoMessage().toByteArray()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BlindVotePayload(BlindVote blindVote, long date, byte[] hash) {
        this.blindVote = blindVote;
        this.date = date;
        this.hash = hash;
    }

    private PB.BlindVotePayload.Builder getBlindVoteBuilder() {
        return PB.BlindVotePayload.newBuilder()
                .setBlindVote(blindVote.toProtoMessage())
                .setDate(date)
                .setHash(ByteString.copyFrom(hash));
    }

    @Override
    public PB.PersistableNetworkPayload toProtoMessage() {
        return PB.PersistableNetworkPayload.newBuilder().setBlindVotePayload(getBlindVoteBuilder()).build();
    }

    public PB.BlindVotePayload toProtoBlindVotePayload() {
        return getBlindVoteBuilder().build();
    }


    public static BlindVotePayload fromProto(PB.BlindVotePayload proto) {
        return new BlindVotePayload(BlindVote.fromProto(proto.getBlindVote()),
                proto.getDate(),
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DateTolerantPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isDateInTolerance() {
        // We don't allow entries older or newer then 5 hours.
        // Preventing forward dating is also important to protect against a sophisticated attack
        return Math.abs(new Date().getTime() - date) <= TOLERANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CapabilityRequiringPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.BLIND_VOTE);
    }

    @Override
    public String toString() {
        return "BlindVotePayload{" +
                "\n     blindVote=" + blindVote +
                ",\n     date=" + new Date(date) +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}

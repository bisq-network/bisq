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
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

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

    public BlindVote(byte[] encryptedVotes,
                     String txId,
                     long stake,
                     byte[] encryptedMeritList) {
        this.encryptedVotes = encryptedVotes;
        this.txId = txId;
        this.stake = stake;
        this.encryptedMeritList = encryptedMeritList;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Used for sending over the network
    @Override
    public PB.BlindVote toProtoMessage() {
        return getBuilder().build();
    }

    @NotNull
    public PB.BlindVote.Builder getBuilder() {
        return PB.BlindVote.newBuilder()
                .setEncryptedVotes(ByteString.copyFrom(encryptedVotes))
                .setTxId(txId)
                .setStake(stake)
                .setEncryptedMeritList(ByteString.copyFrom(encryptedMeritList));
    }

    public static BlindVote fromProto(PB.BlindVote proto) {
        return new BlindVote(proto.getEncryptedVotes().toByteArray(),
                proto.getTxId(),
                proto.getStake(),
                proto.getEncryptedMeritList().toByteArray());
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
                "\n}";
    }
}

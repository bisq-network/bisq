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

package bisq.bridge.grpc.dto;

import bisq.common.Payload;

import com.google.protobuf.ByteString;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class BondedReputationDto implements Payload {
    private final long amount;
    private final byte[] bondedReputationHash;
    private final int lockTime;
    private final String lockupTxId;
    private final Optional<String> unlockTxId; // Only set at unlock tx

    public BondedReputationDto(long amount,
                               byte[] bondedReputationHash,
                               int lockTime,
                               String lockupTxId,
                               Optional<String> unlockTxId) {
        this.amount = amount;
        this.bondedReputationHash = bondedReputationHash;
        this.lockTime = lockTime;
        this.lockupTxId = lockupTxId;
        this.unlockTxId = unlockTxId;
    }

    @Override
    public bisq.bridge.protobuf.BondedReputationDto toProtoMessage() {
        bisq.bridge.protobuf.BondedReputationDto.Builder builder = bisq.bridge.protobuf.BondedReputationDto.newBuilder()
                .setAmount(amount)
                .setBondedReputationHash(ByteString.copyFrom(bondedReputationHash))
                .setLockTime(lockTime)
                .setLockupTxId(lockupTxId);
        unlockTxId.ifPresent(builder::setUnlockTxId);
        return builder.build();
    }

    public static BondedReputationDto fromProto(bisq.bridge.protobuf.BondedReputationDto proto) {
        return new BondedReputationDto(proto.getAmount(),
                proto.getBondedReputationHash().toByteArray(),
                proto.getLockTime(),
                proto.getLockupTxId(),
                proto.hasUnlockTxId() ? Optional.of(proto.getUnlockTxId()) : Optional.empty());
    }
}

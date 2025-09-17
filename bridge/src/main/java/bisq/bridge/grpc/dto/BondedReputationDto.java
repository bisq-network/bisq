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

    public BondedReputationDto(long amount, byte[] bondedReputationHash, int lockTime) {
        this.amount = amount;
        this.bondedReputationHash = bondedReputationHash;
        this.lockTime = lockTime;
    }

    @Override
    public bisq.bridge.protobuf.BondedReputationDto toProtoMessage() {
        return bisq.bridge.protobuf.BondedReputationDto.newBuilder()
                .setAmount(amount)
                .setBondedReputationHash(ByteString.copyFrom(bondedReputationHash))
                .setLockTime(lockTime)
                .build();
    }

    public static BondedReputationDto fromProto(bisq.bridge.protobuf.BondedReputationDto proto) {
        return new BondedReputationDto(proto.getAmount(),
                proto.getBondedReputationHash().toByteArray(),
                proto.getLockTime());
    }
}

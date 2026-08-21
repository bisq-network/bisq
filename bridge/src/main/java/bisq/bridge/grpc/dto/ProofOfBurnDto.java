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
public final class ProofOfBurnDto implements Payload {
    private final long amount;
    private final byte[] proofOfBurnHash;

    public ProofOfBurnDto(long amount, byte[] proofOfBurnHash) {
        this.amount = amount;
        this.proofOfBurnHash = proofOfBurnHash;
    }

    @Override
    public bisq.bridge.protobuf.ProofOfBurnDto toProtoMessage() {
        return bisq.bridge.protobuf.ProofOfBurnDto.newBuilder()
                .setAmount(amount)
                .setProofOfBurnHash(ByteString.copyFrom(proofOfBurnHash))
                .build();
    }

    public static ProofOfBurnDto fromProto(bisq.bridge.protobuf.ProofOfBurnDto proto) {
        return new ProofOfBurnDto(proto.getAmount(), proto.getProofOfBurnHash().toByteArray());
    }
}

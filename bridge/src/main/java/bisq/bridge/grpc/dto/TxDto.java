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
import bisq.common.util.OptionalUtils;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class TxDto implements Payload {
    private final String txId;
    private final Optional<ProofOfBurnDto> proofOfBurnDto;
    private final Optional<BondedReputationDto> bondedReputationDto;

    public TxDto(String txId,
                 Optional<ProofOfBurnDto> proofOfBurnDto,
                 Optional<BondedReputationDto> bondedReputationDto) {
        this.txId = txId;
        this.proofOfBurnDto = proofOfBurnDto;
        this.bondedReputationDto = bondedReputationDto;
    }

    @Override
    public bisq.bridge.protobuf.TxDto toProtoMessage() {
        var builder = bisq.bridge.protobuf.TxDto.newBuilder()
                .setTxId(txId);
        proofOfBurnDto.ifPresent(e -> builder.setProofOfBurnDto(e.toProtoMessage()));
        bondedReputationDto.ifPresent(e -> builder.setBondedReputationDto(e.toProtoMessage()));
        return builder.build();
    }

    public static TxDto fromProto(bisq.bridge.protobuf.TxDto proto) {
        return new TxDto(proto.getTxId(),
                OptionalUtils.optionalIf(proto.hasProofOfBurnDto(), () -> ProofOfBurnDto.fromProto(proto.getProofOfBurnDto())),
                OptionalUtils.optionalIf(proto.hasBondedReputationDto(), () -> BondedReputationDto.fromProto(proto.getBondedReputationDto())));
    }
}

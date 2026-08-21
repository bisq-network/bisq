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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class BurningmanDto implements Payload {
    private final String receiverAddress;
    private final double cappedBurnAmountShare;

    public BurningmanDto(String receiverAddress, double cappedBurnAmountShare) {
        this.receiverAddress = receiverAddress;
        this.cappedBurnAmountShare = cappedBurnAmountShare;
    }

    @Override
    public bisq.bridge.protobuf.BurningmanDto toProtoMessage() {
        return bisq.bridge.protobuf.BurningmanDto.newBuilder()
                .setReceiverAddress(receiverAddress)
                .setCappedBurnAmountShare(cappedBurnAmountShare)
                .build();
    }

    public static BurningmanDto fromProto(bisq.bridge.protobuf.BurningmanDto proto) {
        return new BurningmanDto(proto.getReceiverAddress(), proto.getCappedBurnAmountShare());
    }
}

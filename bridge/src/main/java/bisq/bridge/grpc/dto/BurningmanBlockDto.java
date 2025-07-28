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

import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class BurningmanBlockDto implements Payload {
    private final int height;
    private final List<BurningmanDto> burningmanDtoList;

    public BurningmanBlockDto(int height,
                              List<BurningmanDto> burningmanDtoList) {
        this.height = height;
        this.burningmanDtoList = burningmanDtoList;
    }

    public bisq.bridge.protobuf.BurningmanBlockDto toProtoMessage() {
        return bisq.bridge.protobuf.BurningmanBlockDto.newBuilder()
                .setHeight(height)
                .addAllBurningmanItems(burningmanDtoList.stream().map(BurningmanDto::toProtoMessage).collect(Collectors.toList()))
                .build();
    }

    public static BurningmanBlockDto fromProto(bisq.bridge.protobuf.BurningmanBlockDto proto) {
        return new BurningmanBlockDto(proto.getHeight(),
                proto.getBurningmanItemsList().stream().map(BurningmanDto::fromProto).collect(Collectors.toList()));
    }
}

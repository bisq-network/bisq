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

package bisq.bridge.grpc.messages;

import bisq.common.Payload;

import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;



import bisq.bridge.grpc.dto.BurningmanBlockDto;

@Getter
@EqualsAndHashCode
@ToString
public final class BurningmanBlocksResponse implements Payload {
    private final List<BurningmanBlockDto> blocks;

    public BurningmanBlocksResponse(List<BurningmanBlockDto> blocks) {
        this.blocks = blocks;
    }

    public bisq.bridge.protobuf.BurningmanBlocksResponse toProtoMessage() {
        return bisq.bridge.protobuf.BurningmanBlocksResponse.newBuilder()
                .addAllBurningmanBlocks(blocks.stream().map(BurningmanBlockDto::toProtoMessage).collect(Collectors.toList()))
                .build();
    }

    public static BurningmanBlocksResponse fromProto(bisq.bridge.protobuf.BurningmanBlocksResponse proto) {
        return new BurningmanBlocksResponse(proto.getBurningmanBlocksList().stream().map(BurningmanBlockDto::fromProto).collect(Collectors.toList()));
    }
}

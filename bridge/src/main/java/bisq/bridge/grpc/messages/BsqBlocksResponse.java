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



import bisq.bridge.grpc.dto.BsqBlockDto;

@Getter
@EqualsAndHashCode
@ToString
public final class BsqBlocksResponse implements Payload {
    private final List<BsqBlockDto> blocks;

    public BsqBlocksResponse(List<BsqBlockDto> blocks) {
        this.blocks = blocks;
    }

    public bisq.bridge.protobuf.BsqBlocksResponse toProtoMessage() {
        return bisq.bridge.protobuf.BsqBlocksResponse.newBuilder()
                .addAllBsqBlocks(blocks.stream().map(BsqBlockDto::toProtoMessage).collect(Collectors.toList()))
                .build();
    }

    public static BsqBlocksResponse fromProto(bisq.bridge.protobuf.BsqBlocksResponse proto) {
        return new BsqBlocksResponse(proto.getBsqBlocksList().stream().map(BsqBlockDto::fromProto).collect(Collectors.toList()));
    }
}

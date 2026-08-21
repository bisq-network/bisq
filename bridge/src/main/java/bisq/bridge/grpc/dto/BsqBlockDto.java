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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@EqualsAndHashCode
@ToString
public final class BsqBlockDto implements Payload {
    private final int height;
    private final long time;
    private final List<TxDto> txDtoList;

    public BsqBlockDto(int height,
                       long time,
                       List<TxDto> txDtoList) {
        this.height = height;
        this.time = time;
        this.txDtoList = txDtoList;
    }

    public bisq.bridge.protobuf.BsqBlockDto toProtoMessage() {
        return bisq.bridge.protobuf.BsqBlockDto.newBuilder()
                .setHeight(height)
                .setTime(time)
                .addAllTxDto(txDtoList.stream().map(TxDto::toProtoMessage).collect(Collectors.toList()))
                .build();
    }

    public static BsqBlockDto fromProto(bisq.bridge.protobuf.BsqBlockDto proto) {
        return new BsqBlockDto(proto.getHeight(),
                proto.getTime(),
                proto.getTxDtoList().stream().map(TxDto::fromProto).collect(Collectors.toList()));
    }
}

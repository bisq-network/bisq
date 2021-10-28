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

package bisq.core.dao.state.storage;

import bisq.common.proto.persistable.PersistableEnvelope;

import protobuf.BaseBlock;

import com.google.protobuf.Message;

import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper for list of blocks
 */
@Slf4j
public class BsqBlockStore implements PersistableEnvelope {
    @Getter
    private final List<BaseBlock> blocksAsProto;

    public BsqBlockStore(List<protobuf.BaseBlock> blocksAsProto) {
        this.blocksAsProto = blocksAsProto;
    }

    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setBsqBlockStore(protobuf.BsqBlockStore.newBuilder().addAllBlocks(blocksAsProto))
                .build();
    }

    public static BsqBlockStore fromProto(protobuf.BsqBlockStore proto) {
        return new BsqBlockStore(proto.getBlocksList());
    }
}

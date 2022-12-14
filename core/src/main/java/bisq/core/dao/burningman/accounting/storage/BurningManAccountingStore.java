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

package bisq.core.dao.burningman.accounting.storage;


import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;

import bisq.common.proto.persistable.PersistableEnvelope;

import com.google.protobuf.Message;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class BurningManAccountingStore implements PersistableEnvelope {
    private final LinkedList<AccountingBlock> blocks = new LinkedList<>();

    public BurningManAccountingStore(List<AccountingBlock> blocks) {
        this.blocks.addAll(blocks);
    }

    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setBurningManAccountingStore(protobuf.BurningManAccountingStore.newBuilder()
                        .addAllBlocks(blocks.stream()
                                .map(AccountingBlock::toProtoMessage)
                                .collect(Collectors.toList())))
                .build();
    }

    public static BurningManAccountingStore fromProto(protobuf.BurningManAccountingStore proto) {
        return new BurningManAccountingStore(proto.getBlocksList().stream()
                .map(AccountingBlock::fromProto)
                .collect(Collectors.toList()));
    }
}

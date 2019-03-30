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

package bisq.core.dao.state.model.blockchain;

import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The Block which gets persisted in the DaoState. During parsing transactions can be
 * added to the txs list, therefore it is not an immutable list.
 *
 * It is difficult to make the block immutable by using the same pattern we use with Tx or TxOutput because we add the
 * block at the beginning of the parsing to the daoState and add transactions during parsing. We need to have the state
 * updated during parsing. If we would set then after the parsing the immutable block we might have inconsistent data.
 * There might be a way to do it but it comes with high complexity and risks so for now we prefer to have that known
 * issue with not being fully immutable at that level.
 *
 * An empty block (no BSQ txs) has 146 bytes in Protobuffer serialized form.
 *
 */
@EqualsAndHashCode(callSuper = true)
@Value
public final class Block extends BaseBlock implements PersistablePayload, ImmutableDaoStateModel {
    private final List<Tx> txs;

    public Block(int height, long time, String hash, String previousBlockHash) {
        this(height, time, hash, previousBlockHash, new ArrayList<>());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Block(int height,
                  long time,
                  String hash,
                  String previousBlockHash,
                  List<Tx> txs) {
        super(height,
                time,
                hash,
                previousBlockHash);
        this.txs = txs;
    }


    @Override
    public PB.BaseBlock toProtoMessage() {
        PB.Block.Builder builder = PB.Block.newBuilder()
                .addAllTxs(txs.stream()
                        .map(Tx::toProtoMessage)
                        .collect(Collectors.toList()));
        return getBaseBlockBuilder().setBlock(builder).build();
    }

    public static Block fromProto(PB.BaseBlock proto) {
        PB.Block blockProto = proto.getBlock();
        ImmutableList<Tx> txs = blockProto.getTxsList().isEmpty() ?
                ImmutableList.copyOf(new ArrayList<>()) :
                ImmutableList.copyOf(blockProto.getTxsList().stream()
                        .map(Tx::fromProto)
                        .collect(Collectors.toList()));
        return new Block(proto.getHeight(),
                proto.getTime(),
                proto.getHash(),
                proto.getPreviousBlockHash(),
                txs);
    }

    @Override
    public String toString() {
        return "Block{" +
                "\n     txs=" + txs +
                "\n} " + super.toString();
    }
}

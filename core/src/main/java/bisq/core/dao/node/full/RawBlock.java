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

package bisq.core.dao.node.full;

import bisq.core.dao.state.model.blockchain.BaseBlock;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.common.proto.network.NetworkPayload;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.concurrent.Immutable;

/**
 * A block derived from the BTC blockchain and filtered for BSQ relevant transactions, though the transactions are not
 * verified at that stage. That block is passed to lite nodes over the P2P network. The validation is done by the lite
 * nodes themselves but the transactions are already filtered for BSQ only transactions to keep bandwidth requirements
 * low.
 * Sent over wire.
 */
@Immutable
@EqualsAndHashCode(callSuper = true)
@Value
public final class RawBlock extends BaseBlock implements NetworkPayload {
    // Used when a full node sends a block over the P2P network
    public static RawBlock fromBlock(Block block) {
        ImmutableList<RawTx> txs = ImmutableList.copyOf(block.getTxs().stream().map(RawTx::fromTx).collect(Collectors.toList()));
        return new RawBlock(block.getHeight(),
                block.getTime(),
                block.getHash(),
                block.getPreviousBlockHash(),
                txs);
    }

    private final ImmutableList<RawTx> rawTxs;

    RawBlock(int height,
             long time,
             String hash,
             String previousBlockHash,
             ImmutableList<RawTx> rawTxs) {
        super(height,
                time,
                hash,
                previousBlockHash);
        this.rawTxs = rawTxs;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.BaseBlock toProtoMessage() {
        protobuf.RawBlock.Builder builder = protobuf.RawBlock.newBuilder()
                .addAllRawTxs(rawTxs.stream()
                        .map(RawTx::toProtoMessage)
                        .collect(Collectors.toList()));
        return getBaseBlockBuilder().setRawBlock(builder).build();
    }

    public static RawBlock fromProto(protobuf.BaseBlock proto) {
        protobuf.RawBlock rawBlockProto = proto.getRawBlock();
        ImmutableList<RawTx> rawTxs = rawBlockProto.getRawTxsList().isEmpty() ?
                ImmutableList.copyOf(new ArrayList<>()) :
                ImmutableList.copyOf(rawBlockProto.getRawTxsList().stream()
                        .map(RawTx::fromProto)
                        .collect(Collectors.toList()));
        return new RawBlock(proto.getHeight(),
                proto.getTime(),
                proto.getHash(),
                proto.getPreviousBlockHash(),
                rawTxs);
    }


    @Override
    public String toString() {
        return "RawBlock{" +
                "\n     rawTxs=" + rawTxs +
                "\n} " + super.toString();
    }
}

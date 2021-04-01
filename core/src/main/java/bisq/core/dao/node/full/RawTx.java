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

import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkPayload;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * RawTx as we get it from the RPC service (full node) or from via the P2P network (lite node).
 * It contains pure bitcoin blockchain data without any BSQ specific data.
 * Sent over wire.
 */
@Immutable
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class RawTx extends BaseTx implements NetworkPayload {
    // Used when a full node sends a block over the P2P network
    public static RawTx fromTx(Tx tx) {
        ImmutableList<RawTxOutput> rawTxOutputs = ImmutableList.copyOf(tx.getTxOutputs().stream()
                .map(RawTxOutput::fromTxOutput)
                .collect(Collectors.toList()));

        return new RawTx(tx.getTxVersion(),
                tx.getId(),
                tx.getBlockHeight(),
                tx.getBlockHash(),
                tx.getTime(),
                tx.getTxInputs(),
                rawTxOutputs);
    }

    private final ImmutableList<RawTxOutput> rawTxOutputs;

    // The RPC service is creating a RawTx.
    public RawTx(String id,
                 int blockHeight,
                 String blockHash,
                 long time,
                 ImmutableList<TxInput> txInputs,
                 ImmutableList<RawTxOutput> rawTxOutputs) {
        super(Version.BSQ_TX_VERSION,
                id,
                blockHeight,
                blockHash,
                time,
                txInputs);
        this.rawTxOutputs = rawTxOutputs;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RawTx(String txVersion,
                  String id,
                  int blockHeight,
                  String blockHash,
                  long time,
                  ImmutableList<TxInput> txInputs,
                  ImmutableList<RawTxOutput> rawTxOutputs) {
        super(txVersion,
                id,
                blockHeight,
                blockHash,
                time,
                txInputs);
        this.rawTxOutputs = rawTxOutputs;
    }

    @Override
    public protobuf.BaseTx toProtoMessage() {
        final protobuf.RawTx.Builder builder = protobuf.RawTx.newBuilder()
                .addAllRawTxOutputs(rawTxOutputs.stream()
                        .map(RawTxOutput::toProtoMessage)
                        .collect(Collectors.toList()));
        return getBaseTxBuilder().setRawTx(builder).build();
    }

    public static RawTx fromProto(protobuf.BaseTx protoBaseTx) {
        ImmutableList<TxInput> txInputs = protoBaseTx.getTxInputsList().isEmpty() ?
                ImmutableList.copyOf(new ArrayList<>()) :
                ImmutableList.copyOf(protoBaseTx.getTxInputsList().stream()
                        .map(TxInput::fromProto)
                        .collect(Collectors.toList()));
        protobuf.RawTx protoRawTx = protoBaseTx.getRawTx();
        ImmutableList<RawTxOutput> outputs = protoRawTx.getRawTxOutputsList().isEmpty() ?
                ImmutableList.copyOf(new ArrayList<>()) :
                ImmutableList.copyOf(protoRawTx.getRawTxOutputsList().stream()
                        .map(RawTxOutput::fromProto)
                        .collect(Collectors.toList()));
        return new RawTx(protoBaseTx.getTxVersion(),
                protoBaseTx.getId(),
                protoBaseTx.getBlockHeight(),
                protoBaseTx.getBlockHash(),
                protoBaseTx.getTime(),
                txInputs,
                outputs);
    }

    @Override
    public String toString() {
        return "RawTx{" +
                "\n     rawTxOutputs=" + rawTxOutputs +
                "\n} " + super.toString();
    }
}

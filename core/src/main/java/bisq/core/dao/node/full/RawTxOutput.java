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

import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.TxOutput;

import bisq.common.proto.network.NetworkPayload;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * TxOutput used in RawTx. Containing only immutable bitcoin specific fields.
 * Sent over wire.
 */
@Slf4j
@Immutable
@EqualsAndHashCode(callSuper = true)
@Value
public final class RawTxOutput extends BaseTxOutput implements NetworkPayload {
    public static RawTxOutput fromTxOutput(TxOutput txOutput) {
        return new RawTxOutput(txOutput.getIndex(),
                txOutput.getValue(),
                txOutput.getTxId(),
                txOutput.getPubKeyScript(),
                txOutput.getAddress(),
                txOutput.getOpReturnData(),
                txOutput.getBlockHeight());
    }

    public RawTxOutput(int index,
                       long value,
                       String txId,
                       @Nullable PubKeyScript pubKeyScript,
                       @Nullable String address,
                       @Nullable byte[] opReturnData,
                       int blockHeight) {
        super(index,
                value,
                txId,
                pubKeyScript,
                address,
                opReturnData,
                blockHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.BaseTxOutput toProtoMessage() {
        return getRawTxOutputBuilder().setRawTxOutput(protobuf.RawTxOutput.newBuilder()).build();
    }

    public static RawTxOutput fromProto(protobuf.BaseTxOutput proto) {
        return new RawTxOutput(proto.getIndex(),
                proto.getValue(),
                proto.getTxId(),
                proto.hasPubKeyScript() ? PubKeyScript.fromProto(proto.getPubKeyScript()) : null,
                proto.getAddress().isEmpty() ? null : proto.getAddress(),
                proto.getOpReturnData().isEmpty() ? null : proto.getOpReturnData().toByteArray(),
                proto.getBlockHeight());
    }


    @Override
    public String toString() {
        return "RawTxOutput{} " + super.toString();
    }
}

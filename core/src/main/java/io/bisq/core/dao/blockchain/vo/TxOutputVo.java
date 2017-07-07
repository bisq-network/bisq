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

package io.bisq.core.dao.blockchain.vo;

import com.google.protobuf.ByteString;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.util.JsonExclude;
import io.bisq.core.dao.blockchain.btcd.PubKeyScript;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.Optional;

@Value
public class TxOutputVo implements PersistablePayload {
    private final int index;
    private final long value;
    private final String txId;
    @Nullable
    private final PubKeyScript pubKeyScript;
    @Nullable
    private final String address;
    @Nullable
    @JsonExclude
    private final byte[] opReturnData;
    private final int blockHeight;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.TxOutputVo toProtoMessage() {
        final PB.TxOutputVo.Builder builder = PB.TxOutputVo.newBuilder()
                .setIndex(index)
                .setValue(value)
                .setTxId(txId)
                .setBlockHeight(blockHeight);

        Optional.ofNullable(pubKeyScript).ifPresent(e -> builder.setPubKeyScript(pubKeyScript.toProtoMessage()));
        Optional.ofNullable(address).ifPresent(e -> builder.setAddress(address));
        Optional.ofNullable(opReturnData).ifPresent(e -> builder.setOpReturnData(ByteString.copyFrom(opReturnData)));

        return builder.build();
    }

    public static TxOutputVo fromProto(PB.TxOutputVo proto) {
        return new TxOutputVo(proto.getIndex(),
                proto.getValue(),
                proto.getTxId(),
                proto.hasPubKeyScript() ? PubKeyScript.fromProto(proto.getPubKeyScript()) : null,
                proto.getAddress().isEmpty() ? null : proto.getAddress(),
                proto.getOpReturnData().isEmpty() ? null : proto.getOpReturnData().toByteArray(),
                proto.getBlockHeight());
    }

    public String getId() {
        return txId + ":" + index;
    }

    public TxIdIndexTuple getTxIdIndexTuple() {
        return new TxIdIndexTuple(txId, index);
    }
}

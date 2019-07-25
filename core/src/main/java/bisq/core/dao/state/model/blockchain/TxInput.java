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

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An input is really just a reference to the spending output. It gets identified by the
 * txId and the index of that output. We use TxOutputKey to encapsulate that.
 */
@Immutable
@Value
@EqualsAndHashCode
@Slf4j
public final class TxInput implements PersistablePayload, ImmutableDaoStateModel {
    private final String connectedTxOutputTxId;
    private final int connectedTxOutputIndex;
    @Nullable
    private final String pubKey; // as hex

    public TxInput(String connectedTxOutputTxId, int connectedTxOutputIndex, @Nullable String pubKey) {
        this.connectedTxOutputTxId = connectedTxOutputTxId;
        this.connectedTxOutputIndex = connectedTxOutputIndex;
        this.pubKey = pubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public protobuf.TxInput toProtoMessage() {
        final protobuf.TxInput.Builder builder = protobuf.TxInput.newBuilder()
                .setConnectedTxOutputTxId(connectedTxOutputTxId)
                .setConnectedTxOutputIndex(connectedTxOutputIndex);

        Optional.ofNullable(pubKey).ifPresent(builder::setPubKey);

        return builder.build();
    }

    public static TxInput fromProto(protobuf.TxInput proto) {
        return new TxInput(proto.getConnectedTxOutputTxId(),
                proto.getConnectedTxOutputIndex(),
                proto.getPubKey().isEmpty() ? null : proto.getPubKey());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutputKey getConnectedTxOutputKey() {
        return new TxOutputKey(connectedTxOutputTxId, connectedTxOutputIndex);
    }

    @Override
    public String toString() {
        return "TxInput{" +
                "\n     connectedTxOutputTxId='" + connectedTxOutputTxId + '\'' +
                ",\n     connectedTxOutputIndex=" + connectedTxOutputIndex +
                ",\n     pubKey=" + pubKey +
                "\n}";
    }
}

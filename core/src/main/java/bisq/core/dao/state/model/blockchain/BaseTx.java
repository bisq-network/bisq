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

import com.google.common.collect.ImmutableList;

import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * The base class for the Tx classes with all common immutable data fields.
 *
 * TxOutputs are not added here as the sub classes use different data types.
 * As not all subclasses implement PersistablePayload we leave it to the sub classes to implement the interface.
 * A getBaseTxBuilder method though is available.
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
public abstract class BaseTx implements ImmutableDaoStateModel {
    protected final String txVersion;
    protected final String id;
    protected final int blockHeight;
    protected final String blockHash;
    protected final long time;
    protected final ImmutableList<TxInput> txInputs;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected BaseTx(String txVersion,
                     String id,
                     int blockHeight,
                     String blockHash,
                     long time,
                     ImmutableList<TxInput> txInputs) {
        this.txVersion = txVersion;
        this.id = id;
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
        this.time = time;
        this.txInputs = txInputs;
    }

    protected protobuf.BaseTx.Builder getBaseTxBuilder() {
        return protobuf.BaseTx.newBuilder()
                .setTxVersion(txVersion)
                .setId(id)
                .setBlockHeight(blockHeight)
                .setBlockHash(blockHash)
                .setTime(time)
                .addAllTxInputs(txInputs.stream()
                        .map(TxInput::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        return "BaseTx{" +
                "\n     txVersion='" + txVersion + '\'' +
                ",\n     id='" + id + '\'' +
                ",\n     blockHeight=" + blockHeight +
                ",\n     blockHash='" + blockHash + '\'' +
                ",\n     time=" + time +
                ",\n     txInputs=" + txInputs +
                "\n}";
    }
}

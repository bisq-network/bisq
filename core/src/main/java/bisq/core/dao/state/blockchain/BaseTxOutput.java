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

package bisq.core.dao.state.blockchain;

import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.util.Optional;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Base class for TxOutput classes containing the immutable bitcoin specific blockchain data.
 */
@Slf4j
@Immutable
@Data
public abstract class BaseTxOutput {
    protected final int index;
    protected final long value;
    protected final String txId;

    // Only set if dumpBlockchainData is true
    @Nullable
    protected final PubKeyScript pubKeyScript;
    @Nullable
    protected final String address;
    @Nullable
    @JsonExclude
    protected final byte[] opReturnData;
    protected final int blockHeight;

    BaseTxOutput(int index,
                 long value,
                 String txId,
                 @Nullable PubKeyScript pubKeyScript,
                 @Nullable String address,
                 @Nullable byte[] opReturnData,
                 int blockHeight) {
        this.index = index;
        this.value = value;
        this.txId = txId;
        this.pubKeyScript = pubKeyScript;
        this.address = address;
        this.opReturnData = opReturnData;
        this.blockHeight = blockHeight;

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    PB.BaseTxOutput.Builder getRawTxOutputBuilder() {
        final PB.BaseTxOutput.Builder builder = PB.BaseTxOutput.newBuilder()
                .setIndex(index)
                .setValue(value)
                .setTxId(txId)
                .setBlockHeight(blockHeight);

        Optional.ofNullable(pubKeyScript).ifPresent(e -> builder.setPubKeyScript(pubKeyScript.toProtoMessage()));
        Optional.ofNullable(address).ifPresent(e -> builder.setAddress(address));
        Optional.ofNullable(opReturnData).ifPresent(e -> builder.setOpReturnData(ByteString.copyFrom(opReturnData)));

        return builder;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutputKey getKey() {
        return new TxOutputKey(txId, index);
    }

    @Override
    public String toString() {
        return "BaseTxOutput{" +
                "\n     index=" + index +
                ",\n     value=" + value +
                ",\n     txId='" + txId + '\'' +
                ",\n     pubKeyScript=" + pubKeyScript +
                ",\n     address='" + address + '\'' +
                ",\n     opReturnData=" + Utilities.bytesAsHexString(opReturnData) +
                ",\n     blockHeight=" + blockHeight +
                "\n}";
    }
}

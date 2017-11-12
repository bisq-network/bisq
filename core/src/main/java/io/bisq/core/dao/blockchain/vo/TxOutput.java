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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Utils;

import javax.annotation.Nullable;
import java.util.Optional;

@Data
@Slf4j
public class TxOutput implements PersistablePayload {
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
    private boolean isUnspent;
    private boolean isVerified;
    private TxOutputType txOutputType = TxOutputType.UNDEFINED;
    @Nullable
    private SpentInfo spentInfo;

    public TxOutput(int index,
                    long value,
                    String txId,
                    @Nullable PubKeyScript pubKeyScript,
                    @Nullable String address,
                    @Nullable byte[] opReturnData,
                    int blockHeight) {
        this(index,
                value,
                txId,
                pubKeyScript,
                address,
                opReturnData,
                blockHeight,
                false,
                false,
                TxOutputType.UNDEFINED,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TxOutput(int index,
                     long value,
                     String txId,
                     @Nullable PubKeyScript pubKeyScript,
                     @Nullable String address,
                     @Nullable byte[] opReturnData,
                     int blockHeight,
                     boolean isUnspent,
                     boolean isVerified,
                     TxOutputType txOutputType,
                     @Nullable SpentInfo spentInfo) {
        this.index = index;
        this.value = value;
        this.txId = txId;
        this.pubKeyScript = pubKeyScript;
        this.address = address;
        this.opReturnData = opReturnData;
        this.blockHeight = blockHeight;
        this.isUnspent = isUnspent;
        this.isVerified = isVerified;
        this.txOutputType = txOutputType;
        this.spentInfo = spentInfo;
    }

    public PB.TxOutput toProtoMessage() {
        final PB.TxOutput.Builder builder = PB.TxOutput.newBuilder()
                .setIndex(index)
                .setValue(value)
                .setTxId(txId)
                .setBlockHeight(blockHeight)
                .setIsUnspent(isUnspent)
                .setIsVerified(isVerified)
                .setTxOutputType(txOutputType.toProtoMessage());

        Optional.ofNullable(pubKeyScript).ifPresent(e -> builder.setPubKeyScript(pubKeyScript.toProtoMessage()));
        Optional.ofNullable(address).ifPresent(e -> builder.setAddress(address));
        Optional.ofNullable(opReturnData).ifPresent(e -> builder.setOpReturnData(ByteString.copyFrom(opReturnData)));
        Optional.ofNullable(spentInfo).ifPresent(e -> builder.setSpentInfo(e.toProtoMessage()));

        return builder.build();
    }

    public static TxOutput fromProto(PB.TxOutput proto) {
        return new TxOutput(proto.getIndex(),
                proto.getValue(),
                proto.getTxId(),
                proto.hasPubKeyScript() ? PubKeyScript.fromProto(proto.getPubKeyScript()) : null,
                proto.getAddress().isEmpty() ? null : proto.getAddress(),
                proto.getOpReturnData().isEmpty() ? null : proto.getOpReturnData().toByteArray(),
                proto.getBlockHeight(),
                proto.getIsUnspent(),
                proto.getIsVerified(),
                TxOutputType.fromProto(proto.getTxOutputType()),
                proto.hasSpentInfo() ? SpentInfo.fromProto(proto.getSpentInfo()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void reset() {
        isUnspent = false;
        isVerified = false;
        txOutputType = TxOutputType.UNDEFINED;
        spentInfo = null;
    }

    public boolean isCompensationRequestBtcOutput() {
        return txOutputType == TxOutputType.COMPENSATION_REQUEST_BTC_OUTPUT;
    }

    public boolean isSponsoringBtcOutput() {
        return txOutputType == TxOutputType.SPONSORING_BTC_OUTPUT;
    }

    public String getId() {
        return txId + ":" + index;
    }

    public TxIdIndexTuple getTxIdIndexTuple() {
        return new TxIdIndexTuple(txId, index);
    }

    @Override
    public String toString() {
        return "TxOutput{" +
                "\n     index=" + index +
                ",\n     value=" + value +
                ",\n     txId='" + getId() + '\'' +
                ",\n     pubKeyScript=" + pubKeyScript +
                ",\n     address='" + address + '\'' +
                ",\n     opReturnData=" + (opReturnData != null ? Utils.HEX.encode(opReturnData) : "null") +
                ",\n     blockHeight=" + blockHeight +
                ",\n     isUnspent=" + isUnspent +
                ",\n     isVerified=" + isVerified +
                ",\n     txOutputType=" + txOutputType +
                ",\n     spentInfo=" + (spentInfo != null ? spentInfo.toString() : "null") +
                "\n}";
    }
}

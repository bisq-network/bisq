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

import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.core.dao.blockchain.btcd.PubKeyScript;
import io.bisq.generated.protobuffer.PB;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Utils;

import javax.annotation.Nullable;
import java.util.Optional;

@Data
@AllArgsConstructor
@Slf4j
public class TxOutput implements PersistablePayload {
    private final TxOutputVo txOutputVo;
    private boolean isUnspent;
    private boolean isVerified;
    private TxOutputType txOutputType = TxOutputType.UNDEFINED;
    @Nullable
    private SpentInfo spentInfo;

    public TxOutput(TxOutputVo txOutputVo) {
        this.txOutputVo = txOutputVo;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.TxOutput toProtoMessage() {
        final PB.TxOutput.Builder builder = PB.TxOutput.newBuilder()
                .setTxOutputVo(txOutputVo.toProtoMessage())
                .setIsUnspent(isUnspent)
                .setIsVerified(isVerified)
                .setTxOutputType(txOutputType.toProtoMessage());

        Optional.ofNullable(spentInfo).ifPresent(e -> builder.setSpentInfo(e.toProtoMessage()));

        return builder.build();
    }

    public static TxOutput fromProto(PB.TxOutput proto) {
        return new TxOutput(TxOutputVo.fromProto(proto.getTxOutputVo()),
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

    @Override
    public String toString() {
        return "TxOutput{" +
                "\n     index=" + getIndex() +
                ",\n     value=" + getValue() +
                ",\n     txId='" + getId() + '\'' +
                ",\n     pubKeyScript=" + getPubKeyScript() +
                ",\n     address='" + getAddress() + '\'' +
                ",\n     opReturnData=" + (getOpReturnData() != null ? Utils.HEX.encode(getOpReturnData()) : "null") +
                ",\n     blockHeight=" + getBlockHeight() +
                ",\n     isUnspent=" + isUnspent +
                ",\n     isVerified=" + isVerified +
                ",\n     txOutputType=" + txOutputType +
                ",\n     spentInfo=" + (spentInfo != null ? spentInfo.toString() : "null") +
                "\n}";
    }

    public boolean isCompensationRequestBtcOutput() {
        return txOutputType == TxOutputType.COMPENSATION_REQUEST_BTC_OUTPUT;
    }

    public boolean isSponsoringBtcOutput() {
        return txOutputType == TxOutputType.SPONSORING_BTC_OUTPUT;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getIndex() {
        return txOutputVo.getIndex();
    }

    public long getValue() {
        return txOutputVo.getValue();
    }

    public String getTxId() {
        return txOutputVo.getTxId();
    }

    public PubKeyScript getPubKeyScript() {
        return txOutputVo.getPubKeyScript();
    }

    @Nullable
    public String getAddress() {
        return txOutputVo.getAddress();
    }

    @Nullable
    public byte[] getOpReturnData() {
        return txOutputVo.getOpReturnData();
    }

    public int getBlockHeight() {
        return txOutputVo.getBlockHeight();
    }

    public String getId() {
        return txOutputVo.getId();
    }

    public TxIdIndexTuple getTxIdIndexTuple() {
        return txOutputVo.getTxIdIndexTuple();
    }
}

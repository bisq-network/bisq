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
import lombok.Data;
import lombok.experimental.Delegate;
import org.bitcoinj.core.Utils;

import java.io.Serializable;

@Data
public class TxOutput implements PersistablePayload, Serializable {
    private static final long serialVersionUID = 1;

    @Delegate
    private final TxOutputVo txOutputVo;

    private boolean isUnspent;
    private boolean isVerified;
    private TxOutputType txOutputType;
    private SpentInfo spentInfo;

    public TxOutput(TxOutputVo txOutputVo) {
        this.txOutputVo = txOutputVo;
    }

    public void reset() {
        isUnspent = false;
        isVerified = false;
        txOutputType = null;
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
}

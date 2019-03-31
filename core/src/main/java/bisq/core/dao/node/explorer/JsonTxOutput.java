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

package bisq.core.dao.node.explorer;

import bisq.common.app.Version;

import java.util.Objects;

import lombok.Value;

import javax.annotation.Nullable;

@Value
class JsonTxOutput {
    private final String txVersion = Version.BSQ_TX_VERSION;
    private final String txId;
    private final int index;
    private final long bsqAmount;
    private final long btcAmount;
    private final int height;
    private final boolean isVerified; // isBsqTxOutputType
    private final long burntFee;
    private final long invalidatedBsq;
    private final String address;
    @Nullable
    private final JsonScriptPubKey scriptPubKey;
    @Nullable
    private final JsonSpentInfo spentInfo;
    private final long time;
    private final JsonTxType txType;
    private final String txTypeDisplayString;
    private final JsonTxOutputType txOutputType;
    private final String txOutputTypeDisplayString;
    @Nullable
    private final String opReturn;
    private final int lockTime;
    private final boolean isUnspent;

    JsonTxOutput(String txId, int index, long bsqAmount, long btcAmount, int height, boolean isVerified, long burntFee,
                 long invalidatedBsq, String address, JsonScriptPubKey scriptPubKey, JsonSpentInfo spentInfo,
                 long time, JsonTxType txType, String txTypeDisplayString, JsonTxOutputType txOutputType,
                 String txOutputTypeDisplayString, String opReturn, int lockTime, boolean isUnspent) {
        this.txId = txId;
        this.index = index;
        this.bsqAmount = bsqAmount;
        this.btcAmount = btcAmount;
        this.height = height;
        this.isVerified = isVerified;
        this.burntFee = burntFee;
        this.invalidatedBsq = invalidatedBsq;
        this.address = address;
        this.scriptPubKey = scriptPubKey;
        this.spentInfo = spentInfo;
        this.time = time;
        this.txType = txType;
        this.txTypeDisplayString = txTypeDisplayString;
        this.txOutputType = txOutputType;
        this.txOutputTypeDisplayString = txOutputTypeDisplayString;
        this.opReturn = opReturn;
        this.lockTime = lockTime;
        this.isUnspent = isUnspent;
    }

    String getId() {
        return txId + ":" + index;
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonTxOutput)) return false;
        if (!super.equals(o)) return false;
        JsonTxOutput that = (JsonTxOutput) o;
        return index == that.index &&
                bsqAmount == that.bsqAmount &&
                btcAmount == that.btcAmount &&
                height == that.height &&
                isVerified == that.isVerified &&
                burntFee == that.burntFee &&
                invalidatedBsq == that.invalidatedBsq &&
                time == that.time &&
                lockTime == that.lockTime &&
                isUnspent == that.isUnspent &&
                Objects.equals(txVersion, that.txVersion) &&
                Objects.equals(txId, that.txId) &&
                Objects.equals(address, that.address) &&
                Objects.equals(scriptPubKey, that.scriptPubKey) &&
                Objects.equals(spentInfo, that.spentInfo) &&
                txType.name().equals(that.txType.name()) &&
                Objects.equals(txTypeDisplayString, that.txTypeDisplayString) &&
                txOutputType == that.txOutputType &&
                Objects.equals(txOutputTypeDisplayString, that.txOutputTypeDisplayString) &&
                Objects.equals(opReturn, that.opReturn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), txVersion, txId, index, bsqAmount, btcAmount, height, isVerified,
                burntFee, invalidatedBsq, address, scriptPubKey, spentInfo, time, txType.name(), txTypeDisplayString,
                txOutputType, txOutputTypeDisplayString, opReturn, lockTime, isUnspent);
    }
}

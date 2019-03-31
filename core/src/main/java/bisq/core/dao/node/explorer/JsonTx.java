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

import java.util.List;
import java.util.Objects;

import lombok.Value;

@Value
class JsonTx {
    private final String txVersion = Version.BSQ_TX_VERSION;
    private final String id;
    private final int blockHeight;
    private final String blockHash;
    private final long time;
    private final List<JsonTxInput> inputs;
    private final List<JsonTxOutput> outputs;
    private final JsonTxType txType;
    private final String txTypeDisplayString;
    private final long burntFee;
    private final long invalidatedBsq;
    // If not set it is -1. LockTime of 0 is a valid value.
    private final int unlockBlockHeight;

    JsonTx(String id, int blockHeight, String blockHash, long time, List<JsonTxInput> inputs,
           List<JsonTxOutput> outputs, JsonTxType txType, String txTypeDisplayString, long burntFee,
           long invalidatedBsq, int unlockBlockHeight) {
        this.id = id;
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
        this.time = time;
        this.inputs = inputs;
        this.outputs = outputs;
        this.txType = txType;
        this.txTypeDisplayString = txTypeDisplayString;
        this.burntFee = burntFee;
        this.invalidatedBsq = invalidatedBsq;
        this.unlockBlockHeight = unlockBlockHeight;
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonTx)) return false;
        if (!super.equals(o)) return false;
        JsonTx jsonTx = (JsonTx) o;
        return blockHeight == jsonTx.blockHeight &&
                time == jsonTx.time &&
                burntFee == jsonTx.burntFee &&
                invalidatedBsq == jsonTx.invalidatedBsq &&
                unlockBlockHeight == jsonTx.unlockBlockHeight &&
                Objects.equals(txVersion, jsonTx.txVersion) &&
                Objects.equals(id, jsonTx.id) &&
                Objects.equals(blockHash, jsonTx.blockHash) &&
                Objects.equals(inputs, jsonTx.inputs) &&
                Objects.equals(outputs, jsonTx.outputs) &&
                txType.name().equals(jsonTx.txType.name()) &&
                Objects.equals(txTypeDisplayString, jsonTx.txTypeDisplayString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), txVersion, id, blockHeight, blockHash, time, inputs, outputs,
                txType.name(), txTypeDisplayString, burntFee, invalidatedBsq, unlockBlockHeight);
    }
}

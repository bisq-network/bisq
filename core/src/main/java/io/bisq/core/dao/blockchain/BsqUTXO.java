/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.blockchain;

import lombok.Value;
import org.bitcoinj.script.Script;

import java.util.List;

// Estimation for UTXO set: 1 UTXO object has 78 byte
// 1000 UTXOs - 10 000 UTXOs: 78kb -780kb

@Value
public class BsqUTXO {
    private final String txId;
    private final long index;
    private final long value;
    private final int height;
    private final boolean isBsqCoinBase;
    private final Script script;
    private final String utxoId;

    // Only at raw MS outputs addresses have more then 1 entry 
    // We do not support raw MS for BSQ but lets see if is needed anyway, might be removed 
    private final List<String> addresses;

    private BsqUTXO(String txId, int index, long value, int height, boolean isBsqCoinBase, Script script, List<String> addresses) {
        this.txId = txId;
        this.index = index;
        this.value = value;
        this.height = height;
        this.isBsqCoinBase = isBsqCoinBase;
        this.script = script;
        this.addresses = addresses;

        utxoId = txId + ":" + index;
    }

    public BsqUTXO(String txId, int height, boolean isBsqCoinBase, TxOutput output) {
        this(txId,
                output.getIndex(),
                output.getValue(),
                height,
                isBsqCoinBase,
                output.getScript(),
                output.getAddresses());
    }

    @Override
    public String toString() {
        return "BsqUTXO{" +
                "\n     txId='" + txId + '\'' +
                ",\n     index=" + index +
                ",\n     value=" + value +
                ",\n     height=" + height +
                ",\n     isBsqCoinBase=" + isBsqCoinBase +
                ",\n     script=" + script +
                ",\n     utxoId='" + utxoId + '\'' +
                ",\n     addresses=" + addresses +
                "\n}";
    }
}

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
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;

// Estimation for UTXO set: 1 UTXO object has 78 byte
// 1000 UTXOs - 10 000 UTXOs: 78kb -780kb

@Value
@Slf4j
public class BsqUTXO implements Serializable {
    private final int height;
    private final TxOutput txOutput;
    private final boolean isBsqCoinBase;
    private final String utxoId;

    public BsqUTXO(int height, TxOutput txOutput, boolean isBsqCoinBase) {
        this.height = height;
        this.txOutput = txOutput;
        this.isBsqCoinBase = isBsqCoinBase;

        utxoId = txOutput.getTxId() + ":" + txOutput.getIndex();
    }

    public String getAddress() {
        // Only at raw MS outputs addresses have more then 1 entry 
        // We do not support raw MS for BSQ but lets see if is needed anyway, might be removed 
        final List<String> addresses = txOutput.getAddresses();
        return addresses.size() == 1 ? addresses.get(0) : addresses.toString();
    }

    public long getValue() {
        return txOutput.getValue();
    }

    public String getTxId() {
        return txOutput.getTxId();
    }

    public int getIndex() {
        return txOutput.getIndex();
    }

    @Override
    public String toString() {
        return "BsqUTXO{" +
                "\n     height=" + height +
                ", \n     output=" + txOutput +
                ", \n     isBsqCoinBase=" + isBsqCoinBase +
                ", \n     utxoId='" + utxoId + '\'' +
                "\n}";
    }

}

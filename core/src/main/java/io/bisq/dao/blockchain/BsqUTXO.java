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

package io.bisq.dao.blockchain;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Estimation for UTXO set: 1 UTXO object has 78 byte
// 1000 UTXOs - 10 000 UTXOs: 78kb -780kb

public class BsqUTXO extends UTXO {
    private static final Logger log = LoggerFactory.getLogger(BsqUTXO.class);

    public BsqUTXO(String txId, long index, Coin value, int height, boolean coinBase, Script script, String address) {
        super(Sha256Hash.wrap(Utils.HEX.decode(txId)), index, value, height, coinBase, script, address);
    }

    @Override
    public String toString() {
        return String.format("value:%d, spending tx:%s at index:%d)", getValue().value, getHash(), getIndex());
    }
}

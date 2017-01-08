/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.blockchain;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class SquUTXO extends UTXO {
    private static final Logger log = LoggerFactory.getLogger(SquUTXO.class);


    public SquUTXO(Sha256Hash hash, long index, Coin value, int height, boolean coinbase, Script script) {
        super(hash, index, value, height, coinbase, script);
    }

    public SquUTXO(Sha256Hash hash, long index, Coin value, int height, boolean coinbase, Script script, String address) {
        super(hash, index, value, height, coinbase, script, address);
    }

    public SquUTXO(InputStream in) throws IOException {
        super(in);
    }

    @Override
    public String toString() {
        return String.format("value:%d, spending tx:%s at index:%d)", getValue().value, getHash(), getIndex());
    }
}

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

package io.bisq.core.dao.blockchain;

import lombok.Value;

import java.util.HashMap;

public class BsqUTXOMap extends HashMap<BsqUTXOMap.TxIdIndexTuple, BsqUTXO> {
    public boolean containsTuple(String txId, int index) {
        return super.containsKey(new TxIdIndexTuple(txId, index));
    }

    public Object putByTuple(String txId, int index, BsqUTXO bsqUTXO) {
        return super.put(new TxIdIndexTuple(txId, index), bsqUTXO);
    }

    public BsqUTXO getByTuple(String txId, int index) {
        return super.get(new TxIdIndexTuple(txId, index));
    }

    public BsqUTXO removeByTuple(String txId, int index) {
        return super.remove(new TxIdIndexTuple(txId, index));
    }

    @Value
    static class TxIdIndexTuple {
        private final String txId;
        private final int index;

        @Override
        public String toString() {
            return txId + ":" + index;
        }
    }
}

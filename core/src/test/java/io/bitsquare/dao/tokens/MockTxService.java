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

package io.bitsquare.dao.tokens;

import java.util.HashMap;
import java.util.Map;

class MockTxService extends TxService {
    private Map<String, Tx> txMap = new HashMap<>();

    public MockTxService() {
    }

    public Tx getTx(String txId) {
        return txMap.get(txId);
    }

    public void addTx(Tx tx) {
        txMap.put(tx.id, tx);
    }

    public void cleanup() {
        txMap = new HashMap<>();
    }
}

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

package io.bitsquare.dao.tokens;

import java.util.HashMap;
import java.util.Map;

class MockTxService extends TxService {
    private final Tx genesisTx;
    private Map<String, Tx> txMap = new HashMap<>();

    public MockTxService(Tx genesisTx) {
        txMap.put(genesisTx.id, genesisTx);
        this.genesisTx = genesisTx;
    }

    public Tx getTx(String txId) {
        return txMap.get(txId);
    }
}

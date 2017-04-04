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

import lombok.Getter;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
public class BsqBlock {
    @Getter
    private final int height;
    @Getter
    private final List<String> txIds;
    // private final Map<String, Tx> txByTxIdMap = new HashMap<>();
    private final List<Tx> txList = new ArrayList<>();
    
    public BsqBlock(List<String> txIds, int height) {
        this.txIds = txIds;
        this.height = height;
    }

    public void addTx(Tx tx) {
        //txByTxIdMap.put(tx.getId(), tx);
        txList.add(tx);
    }

  /*  public Tx getTxByTxId(String txId) {
        return txByTxIdMap.get(txId);
    }*/

    @Override
    public String toString() {
        return "BsqBlock{" +
                "\nheight=" + height +
                ",\ntxIds=" + txIds +
                ",\ntxList=" + txList +
                "}\n";
    }
}

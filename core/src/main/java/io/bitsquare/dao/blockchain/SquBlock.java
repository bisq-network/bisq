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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SquBlock {
    private static final Logger log = LoggerFactory.getLogger(SquBlock.class);

    public final int blockHeight;
    public final List<String> txIds;

    private Map<String, SquTransaction> squTransactions = new HashMap<>();

    public SquBlock(List<String> txIds, int blockHeight) {
        this.txIds = txIds;
        this.blockHeight = blockHeight;
    }

    public void addSquTransaction(SquTransaction squTransaction) {
        squTransactions.put(squTransaction.txId, squTransaction);
    }

    public SquTransaction getSquTransaction(String txId) {
        return squTransactions.get(txId);
    }

    public Map<String, SquTransaction> getTransactions() {
        return squTransactions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SquBlock squBlock = (SquBlock) o;

        if (blockHeight != squBlock.blockHeight) return false;
        if (txIds != null ? !txIds.equals(squBlock.txIds) : squBlock.txIds != null) return false;
        return !(squTransactions != null ? !squTransactions.equals(squBlock.squTransactions) : squBlock.squTransactions != null);

    }

    @Override
    public int hashCode() {
        int result = blockHeight;
        result = 31 * result + (txIds != null ? txIds.hashCode() : 0);
        result = 31 * result + (squTransactions != null ? squTransactions.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SquBlock{" +
                "blockHeight=" + blockHeight +
                ", txIds=" + txIds +
                ", squTransactions=" + squTransactions +
                '}';
    }
}

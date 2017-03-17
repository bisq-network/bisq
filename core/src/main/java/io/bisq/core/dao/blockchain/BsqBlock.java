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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BsqBlock {
    private static final Logger log = LoggerFactory.getLogger(BsqBlock.class);

    public final int blockHeight;
    public final List<String> txIds;

    private Map<String, BsqTransaction> bsqTransactions = new HashMap<>();

    public BsqBlock(List<String> txIds, int blockHeight) {
        this.txIds = txIds;
        this.blockHeight = blockHeight;
    }

    public void addBsqTransaction(BsqTransaction bsqTransaction) {
        bsqTransactions.put(bsqTransaction.txId, bsqTransaction);
    }

    public BsqTransaction getBsqTransaction(String txId) {
        return bsqTransactions.get(txId);
    }

    public Map<String, BsqTransaction> getTransactions() {
        return bsqTransactions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BsqBlock bsqBlock = (BsqBlock) o;

        if (blockHeight != bsqBlock.blockHeight) return false;
        if (txIds != null ? !txIds.equals(bsqBlock.txIds) : bsqBlock.txIds != null) return false;
        return !(bsqTransactions != null ? !bsqTransactions.equals(bsqBlock.bsqTransactions) : bsqBlock.bsqTransactions != null);

    }

    @Override
    public int hashCode() {
        int result = blockHeight;
        result = 31 * result + (txIds != null ? txIds.hashCode() : 0);
        result = 31 * result + (bsqTransactions != null ? bsqTransactions.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BsqBlock{" +
                "blockHeight=" + blockHeight +
                ", txIds=" + txIds +
                ", bsqTransactions=" + bsqTransactions +
                '}';
    }
}

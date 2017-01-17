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

public class SquTxInput {
    private static final Logger log = LoggerFactory.getLogger(SquTxInput.class);

    public final int spendingOuptuIndex;
    public final String spendingTxId;
    public final String txId;

    public SquTxInput(int spendingOuptuIndex, String spendingTxId, String txId) {
        this.spendingOuptuIndex = spendingOuptuIndex;
        this.spendingTxId = spendingTxId;
        this.txId = txId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SquTxInput that = (SquTxInput) o;

        if (spendingOuptuIndex != that.spendingOuptuIndex) return false;
        if (spendingTxId != null ? !spendingTxId.equals(that.spendingTxId) : that.spendingTxId != null) return false;
        return !(txId != null ? !txId.equals(that.txId) : that.txId != null);

    }

    @Override
    public int hashCode() {
        int result = spendingOuptuIndex;
        result = 31 * result + (spendingTxId != null ? spendingTxId.hashCode() : 0);
        result = 31 * result + (txId != null ? txId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SquTxInput{" +
                "spendingOuptuIndex=" + spendingOuptuIndex +
                ", spendingTxId='" + spendingTxId + '\'' +
                ", txId='" + txId + '\'' +
                '}';
    }
}

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

package io.bisq.dao.tokens;

import java.util.UUID;

public class TxOutput {
    public final String id;
    public Tx tx;
    public TxInput inputOfSpendingTx;
    public boolean isSpent;
    public String address;
    public long value;
    public int index;
    public boolean isToken;

    public TxOutput(String address, long value) {
        this.address = address;
        this.value = value;
        id = UUID.randomUUID().toString();
    }

    @Override
    public String toString() {
        return "TxOutput{" +
                "tx.id=" + (tx != null ? tx.id : "null") +
                ", inputOfSpendingTx.id=" + (inputOfSpendingTx != null ? inputOfSpendingTx.id : "null") +
                ", isSpent=" + isSpent +
                ", address='" + address + '\'' +
                ", value=" + value +
                ", index=" + index +
                ", isToken=" + isToken +
                '}';
    }
}

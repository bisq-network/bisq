/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.burningman.model;

import java.util.Date;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class ReimbursementModel {
    private final long amount;
    private final int height;
    private final long date;
    private final int cycleIndex;
    private final String txId;

    public ReimbursementModel(long amount,
                              int height,
                              long date,
                              int cycleIndex,
                              String txId) {
        this.amount = amount;
        this.height = height;
        this.date = date;
        this.cycleIndex = cycleIndex;
        this.txId = txId;
    }

    @Override
    public String toString() {
        return "\n          ReimbursementModel{" +
                ",\r\n               amount=" + amount +
                ",\r\n               height=" + height +
                ",\r\n               date=" + new Date(date) +
                ",\r\n               cycleIndex=" + cycleIndex +
                ",\r\n               txId=" + txId +
                "\r\n          }";
    }
}

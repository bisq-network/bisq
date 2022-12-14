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

package bisq.core.dao.burningman.accounting.balance;

import bisq.common.util.DateUtil;

import java.util.Date;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode
@Getter
public abstract class BaseBalanceEntry implements BalanceEntry {
    private final String txId;
    private final long amount;
    private final Date date;
    private final Date month;
    private final Type type;

    protected BaseBalanceEntry(String txId, long amount, Date date, Type type) {
        this.txId = txId;
        this.amount = amount;
        this.date = date;
        month = DateUtil.getStartOfMonth(date);
        this.type = type;
    }

    @Override
    public String toString() {
        return "BaseBalanceEntry{" +
                "\r\n     txId=" + txId +
                "\r\n     amount=" + amount +
                ",\r\n     date=" + date +
                ",\r\n     month=" + month +
                ",\r\n     type=" + type +
                "\r\n}";
    }
}

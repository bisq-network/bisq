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

package bisq.desktop.main.dao.burnbsq.burningman;

import bisq.desktop.util.DisplayUtils;

import bisq.core.dao.burningman.model.ReimbursementModel;
import bisq.core.util.coin.BsqFormatter;

import java.util.Date;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
class ReimbursementListItem {
    private final String amountAsString, dateAsString;
    private final long amount, date;
    private final int height, cycleIndex;

    ReimbursementListItem(ReimbursementModel model, BsqFormatter bsqFormatter) {
        height = model.getHeight();
        cycleIndex = model.getCycleIndex();
        date = model.getDate();
        dateAsString = DisplayUtils.formatDateTime(new Date(date));
        amount = model.getAmount();
        amountAsString = bsqFormatter.formatCoinWithCode(amount);
    }
}

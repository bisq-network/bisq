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

package bisq.desktop.main.dao.burnbsq.burningmen;

import bisq.desktop.util.DisplayUtils;

import bisq.core.dao.burningman.BurnOutputModel;
import bisq.core.util.coin.BsqFormatter;

import java.util.Date;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
class BurnOutputListItem {
    private final BurnOutputModel burnOutputModel;
    private final String dateAsString, txId, amountAsString, decayedAmountAsString;
    private final long date, amount, decayedAmount;
    private final int cycleIndex, height;

    BurnOutputListItem(BurnOutputModel model, BsqFormatter bsqFormatter) {
        this.burnOutputModel = model;

        height = model.getHeight();
        cycleIndex = model.getCycleIndex();
        date = model.getDate();
        dateAsString = DisplayUtils.formatDateTime(new Date(date));
        txId = model.getTxId();
        amount = model.getAmount();
        amountAsString = bsqFormatter.formatCoinWithCode(amount);
        decayedAmount = model.getDecayedAmount();
        decayedAmountAsString = bsqFormatter.formatCoinWithCode(decayedAmount);
    }
}

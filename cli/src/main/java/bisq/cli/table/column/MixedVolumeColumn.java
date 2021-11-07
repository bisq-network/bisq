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

package bisq.cli.table.column;

import static bisq.cli.CurrencyFormat.formatCryptoCurrencyVolume;
import static bisq.cli.CurrencyFormat.formatFiatVolume;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;

/**
 * For displaying a mix of fiat and altcoin volumes with appropriate precision.
 */
public class MixedVolumeColumn extends LongColumn {

    public MixedVolumeColumn(String name) {
        super(name, RIGHT);
    }

    @Override
    public void addRow(Long value) {
        throw new UnsupportedOperationException("use public void addRow(Long value, boolean isAltcoinVolume) instead");
    }

    @Deprecated
    public void addRow(Long value, boolean isAltcoinVolume) {
        rows.add(value);

        String s = isAltcoinVolume
                ? formatCryptoCurrencyVolume(value)
                : formatFiatVolume(value);
        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    public void addRow(Long value, int displayPrecision) {
        rows.add(value);

        boolean isAltcoinVolume = displayPrecision > 0;
        String s = isAltcoinVolume
                ? formatCryptoCurrencyVolume(value, displayPrecision)
                : formatFiatVolume(value);
        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return getRow(rowIndex).toString();
    }

    @Override
    public StringColumn asStringColumn() {
        return stringColumn.justify();
    }
}

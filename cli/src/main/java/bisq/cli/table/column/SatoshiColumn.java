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

import static bisq.cli.CurrencyFormat.formatBsq;
import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;

/**
 * For displaying BTC or BSQ satoshi values with appropriate precision.
 */
public class SatoshiColumn extends LongColumn {

    protected final boolean isBsqSatoshis;

    // The default SatoshiColumn JUSTIFICATION is RIGHT.
    public SatoshiColumn(String name) {
        this(name, RIGHT, false);
    }

    public SatoshiColumn(String name, boolean isBsqSatoshis) {
        this(name, RIGHT, isBsqSatoshis);
    }

    public SatoshiColumn(String name, JUSTIFICATION justification) {
        this(name, justification, false);
    }

    public SatoshiColumn(String name, JUSTIFICATION justification, boolean isBsqSatoshis) {
        super(name, justification);
        this.isBsqSatoshis = isBsqSatoshis;
    }

    @Override
    public void addRow(Long value) {
        rows.add(value);

        // We do not know how much padding each StringColumn value needs until it has all the values.
        String s = isBsqSatoshis ? formatBsq(value) : formatSatoshis(value);
        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return isBsqSatoshis
                ? formatBsq(getRow(rowIndex))
                : formatSatoshis(getRow(rowIndex));
    }

    @Override
    public StringColumn asStringColumn() {
        return stringColumn.justify();
    }
}

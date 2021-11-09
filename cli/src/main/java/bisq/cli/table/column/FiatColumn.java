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

import java.util.stream.IntStream;

import static bisq.cli.CurrencyFormat.formatFiatVolume;
import static bisq.cli.CurrencyFormat.formatPrice;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;
import static bisq.cli.table.column.FiatColumn.DISPLAY_MODE.PRICE;
import static bisq.cli.table.column.FiatColumn.DISPLAY_MODE.TRIGGER_PRICE;

/**
 * For displaying fiat values as volume, price, or optional trigger price
 * with appropriate precision.
 */
public class FiatColumn extends LongColumn {

    public enum DISPLAY_MODE {
        PRICE,
        TRIGGER_PRICE,
        VOLUME
    }

    private final DISPLAY_MODE displayMode;

    // The default FiatColumn JUSTIFICATION is RIGHT.
    // The default FiatColumn DISPLAY_MODE is PRICE.
    public FiatColumn(String name) {
        this(name, RIGHT, PRICE);
    }

    public FiatColumn(String name, DISPLAY_MODE displayMode) {
        this(name, RIGHT, displayMode);
    }

    public FiatColumn(String name,
                      JUSTIFICATION justification,
                      DISPLAY_MODE displayMode) {
        super(name, justification);
        this.displayMode = displayMode;
    }

    @Override
    public void addRow(Long value) {
        rows.add(value);

        String s;
        if (displayMode.equals(TRIGGER_PRICE))
            s = value > 0 ? formatPrice(value) : "";
        else
            s = displayMode.equals(PRICE) ? formatPrice(value) : formatFiatVolume(value);

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
        // We cached the formatted fiat price strings, but we did
        // not know how much padding each string needed until now.
        IntStream.range(0, stringColumn.getRows().size()).forEach(rowIndex -> {
            String unjustified = stringColumn.getRow(rowIndex);
            String justified = stringColumn.toJustifiedString(unjustified);
            stringColumn.updateRow(rowIndex, justified);
        });
        return this.stringColumn;
    }
}

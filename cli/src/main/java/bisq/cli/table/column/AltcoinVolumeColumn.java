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

import java.math.BigDecimal;

import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;

/**
 * For displaying altcoin volume with appropriate precision.
 */
public class AltcoinVolumeColumn extends LongColumn {

    public enum DISPLAY_MODE {
        ALTCOIN_VOLUME,
        BSQ_VOLUME,
    }

    private final DISPLAY_MODE displayMode;

    // The default AltcoinVolumeColumn JUSTIFICATION is RIGHT.
    public AltcoinVolumeColumn(String name, DISPLAY_MODE displayMode) {
        this(name, RIGHT, displayMode);
    }

    public AltcoinVolumeColumn(String name,
                               JUSTIFICATION justification,
                               DISPLAY_MODE displayMode) {
        super(name, justification);
        this.displayMode = displayMode;
    }

    @Override
    public void addRow(Long value) {
        rows.add(value);

        String s = toFormattedString.apply(value, displayMode);
        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return toFormattedString.apply(getRow(rowIndex), displayMode);
    }

    @Override
    public StringColumn asStringColumn() {
        // We cached the formatted altcoin value strings, but we did
        // not know how much padding each string needed until now.
        IntStream.range(0, stringColumn.getRows().size()).forEach(rowIndex -> {
            String unjustified = stringColumn.getRow(rowIndex);
            String justified = stringColumn.toJustifiedString(unjustified);
            stringColumn.updateRow(rowIndex, justified);
        });
        return this.stringColumn;
    }

    private final BiFunction<Long, DISPLAY_MODE, String> toFormattedString = (value, displayMode) -> {
        switch (displayMode) {
            case ALTCOIN_VOLUME:
                return value > 0 ? new BigDecimal(value).movePointLeft(8).toString() : "";
            case BSQ_VOLUME:
                return value > 0 ? new BigDecimal(value).movePointLeft(2).toString() : "";
            default:
                throw new IllegalStateException("invalid display mode: " + displayMode);
        }
    };
}

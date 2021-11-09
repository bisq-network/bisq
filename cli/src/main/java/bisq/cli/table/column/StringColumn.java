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


import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static bisq.cli.table.column.Column.JUSTIFICATION.LEFT;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;

/**
 * For displaying justified string values.
 */
public class StringColumn extends AbstractColumn<StringColumn, String> {

    private final List<String> rows = new ArrayList<>();

    private final Predicate<String> isNewMaxWidth = (s) -> s != null && !s.isEmpty() && s.length() > maxWidth;

    // The default StringColumn JUSTIFICATION is LEFT.
    public StringColumn(String name) {
        this(name, LEFT);
    }

    // Use this constructor to override default LEFT justification.
    public StringColumn(String name, JUSTIFICATION justification) {
        super(name, justification);
        this.maxWidth = name.length();
    }

    @Override
    public void addRow(String value) {
        rows.add(value);
        if (isNewMaxWidth.test(value))
            maxWidth = value.length();
    }

    @Override
    public List<String> getRows() {
        return rows;
    }

    @Override
    public int rowCount() {
        return rows.size();
    }

    @Override
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public String getRow(int rowIndex) {
        return rows.get(rowIndex);
    }

    @Override
    public void updateRow(int rowIndex, String newValue) {
        rows.set(rowIndex, newValue);
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return getRow(rowIndex);
    }

    @Override
    public StringColumn asStringColumn() {
        return this;
    }

    @Override
    public StringColumn justify() {
        if (justification.equals(RIGHT)) {
            IntStream.range(0, getRows().size()).forEach(rowIndex -> {
                String unjustified = getRow(rowIndex);
                String justified = toJustifiedString(unjustified);
                updateRow(rowIndex, justified);
            });
        }
        return this;
    }
}

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

/**
 * For displaying boolean values as YES, NO, or user's choice for 'true' and 'false'.
 */
public class BooleanColumn extends AbstractColumn<BooleanColumn, Boolean> {

    private static final String DEFAULT_TRUE_AS_STRING = "YES";
    private static final String DEFAULT_FALSE_AS_STRING = "NO";

    private final List<Boolean> rows = new ArrayList<>();

    private final Predicate<String> isNewMaxWidth = (s) -> s != null && !s.isEmpty() && s.length() > maxWidth;

    private final String trueAsString;
    private final String falseAsString;

    // The default BooleanColumn JUSTIFICATION is LEFT.
    // The default BooleanColumn True AsString value is YES.
    // The default BooleanColumn False AsString value is NO.
    public BooleanColumn(String name) {
        this(name, LEFT, DEFAULT_TRUE_AS_STRING, DEFAULT_FALSE_AS_STRING);
    }

    // Use this constructor to override default LEFT justification.
    @SuppressWarnings("unused")
    public BooleanColumn(String name, JUSTIFICATION justification) {
        this(name, justification, DEFAULT_TRUE_AS_STRING, DEFAULT_FALSE_AS_STRING);
    }

    // Use this constructor to override default true/false as string defaults.
    public BooleanColumn(String name, String trueAsString, String falseAsString) {
        this(name, LEFT, trueAsString, falseAsString);
    }

    // Use this constructor to override default LEFT justification.
    public BooleanColumn(String name,
                         JUSTIFICATION justification,
                         String trueAsString,
                         String falseAsString) {
        super(name, justification);
        this.trueAsString = trueAsString;
        this.falseAsString = falseAsString;
        this.maxWidth = name.length();
    }

    @Override
    public void addRow(Boolean value) {
        rows.add(value);

        // We do not know how much padding each StringColumn value needs until it has all the values.
        String s = asString(value);
        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    @Override
    public List<Boolean> getRows() {
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
    public Boolean getRow(int rowIndex) {
        return rows.get(rowIndex);
    }

    @Override
    public void updateRow(int rowIndex, Boolean newValue) {
        rows.set(rowIndex, newValue);
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return getRow(rowIndex)
                ? trueAsString
                : falseAsString;
    }

    @Override
    public StringColumn asStringColumn() {
        // We cached the formatted satoshi strings, but we did
        // not know how much padding each string needed until now.
        IntStream.range(0, stringColumn.getRows().size()).forEach(rowIndex -> {
            String unjustified = stringColumn.getRow(rowIndex);
            String justified = stringColumn.toJustifiedString(unjustified);
            stringColumn.updateRow(rowIndex, justified);
        });
        return stringColumn;
    }

    private String asString(boolean value) {
        return value ? trueAsString : falseAsString;
    }
}

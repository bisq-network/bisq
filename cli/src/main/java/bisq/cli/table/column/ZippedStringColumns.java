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
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import static bisq.cli.table.column.ZippedStringColumns.DUPLICATION_MODE.EXCLUDE_DUPLICATES;
import static bisq.cli.table.column.ZippedStringColumns.DUPLICATION_MODE.INCLUDE_DUPLICATES;



import bisq.cli.table.column.Column.JUSTIFICATION;

/**
 * For zipping multiple StringColumns into a single StringColumn.
 * Useful for displaying amount and volume range values.
 */
public class ZippedStringColumns {

    public enum DUPLICATION_MODE {
        EXCLUDE_DUPLICATES,
        INCLUDE_DUPLICATES
    }

    private final String name;
    private final JUSTIFICATION justification;
    private final String delimiter;
    private final StringColumn[] columns;

    public ZippedStringColumns(String name,
                               JUSTIFICATION justification,
                               String delimiter,
                               StringColumn... columns) {
        this.name = name;
        this.justification = justification;
        this.delimiter = delimiter;
        this.columns = columns;
        validateColumnData();
    }

    public StringColumn asStringColumn(DUPLICATION_MODE duplicationMode) {
        StringColumn stringColumn = new StringColumn(name, justification);

        buildRows(stringColumn, duplicationMode);

        // Re-set the column name field to its justified value, in case any of the column
        // values are longer than the name passed to this constructor.
        stringColumn.setName(stringColumn.toJustifiedString(name));

        return stringColumn;
    }

    private void buildRows(StringColumn stringColumn, DUPLICATION_MODE duplicationMode) {
        // Populate the StringColumn with unjustified zipped values;  we cannot justify
        // the zipped values until stringColumn knows its final maxWidth.
        IntStream.range(0, columns[0].getRows().size()).forEach(rowIndex -> {
            String row = buildRow(rowIndex, duplicationMode);
            stringColumn.addRow(row);
        });

        formatRows(stringColumn);
    }

    private String buildRow(int rowIndex, DUPLICATION_MODE duplicationMode) {
        StringBuilder rowBuilder = new StringBuilder();
        @Nullable
        List<String> processedValues = duplicationMode.equals(EXCLUDE_DUPLICATES)
                ? new ArrayList<>()
                : null;
        IntStream.range(0, columns.length).forEachOrdered(colIndex -> {
            // For each column @ rowIndex ...
            var value = columns[colIndex].getRows().get(rowIndex);
            if (duplicationMode.equals(INCLUDE_DUPLICATES)) {
                if (rowBuilder.length() > 0)
                    rowBuilder.append(delimiter);

                rowBuilder.append(value);
            } else if (!processedValues.contains(value)) {
                if (rowBuilder.length() > 0)
                    rowBuilder.append(delimiter);

                rowBuilder.append(value);
                processedValues.add(value);
            }
        });
        return rowBuilder.toString();
    }

    private void formatRows(StringColumn stringColumn) {
        // Now we can justify the zipped string values in the new StringColumn.
        IntStream.range(0, stringColumn.getRows().size()).forEach(rowIndex -> {
            String unjustified = stringColumn.getRow(rowIndex);
            String justified = stringColumn.toJustifiedString(unjustified);
            stringColumn.updateRow(rowIndex, justified);
        });
    }

    private void validateColumnData() {
        if (columns.length == 0)
            throw new IllegalStateException("cannot zip columns because they do not have any data");

        StringColumn firstColumn = columns[0];
        if (firstColumn.getRows().isEmpty())
            throw new IllegalStateException("1st column has no data");

        IntStream.range(1, columns.length).forEach(colIndex -> {
            if (columns[colIndex].getRows().size() != firstColumn.getRows().size())
                throw new IllegalStateException("columns do not have same number of rows");
        });
    }
}

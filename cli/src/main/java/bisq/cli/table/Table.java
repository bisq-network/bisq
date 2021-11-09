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

package bisq.cli.table;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import java.util.stream.IntStream;

import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;
import static com.google.common.base.Strings.padStart;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;



import bisq.cli.table.column.Column;

/**
 * A simple table of formatted data for the CLI's output console.  A table must be
 * created with at least one populated column, and each column passed to the constructor
 * must contain the same number of rows.  Null checking is omitted because tables are
 * populated by protobuf message fields which cannot be null.
 *
 * All data in a column has the same type: long, string, etc., but a table
 * may contain an arbitrary number of columns of any type.  For output formatting
 * purposes, numeric and date columns should be transformed to a StringColumn type with
 * formatted and justified string values before being passed to the constructor.
 *
 * This is not a relational, rdbms table.
 */
public class Table {

    public final Column<?>[] columns;
    public final int rowCount;

    // Each printed column is delimited by two spaces.
    private final int columnDelimiterLength = 2;

    /**
     * Default constructor.  Takes populated Columns.
     *
     * @param columns containing the same number of rows
     */
    public Table(Column<?>... columns) {
        this.columns = columns;
        this.rowCount = columns.length > 0 ? columns[0].rowCount() : 0;
        validateStructure();
    }

    /**
     * Print table data to a PrintStream.
     *
     * @param printStream the target output stream
     */
    public void print(PrintStream printStream) {
        printColumnNames(printStream);
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            printRow(printStream, rowIndex);
        }
    }

    /**
     * Print table column names to a PrintStream.
     *
     * @param printStream the target output stream
     */
    private void printColumnNames(PrintStream printStream) {
        IntStream.range(0, columns.length).forEachOrdered(colIndex -> {
            var c = columns[colIndex];
            var justifiedName = c.getJustification().equals(RIGHT)
                    ? padStart(c.getName(), c.getWidth(), ' ')
                    : c.getName();
            var paddedWidth = colIndex == columns.length - 1
                    ? c.getName().length()
                    : c.getWidth() + columnDelimiterLength;
            printStream.printf("%-" + paddedWidth + "s", justifiedName);
        });
        printStream.println();
    }

    /**
     * Print a table row to a PrintStream.
     *
     * @param printStream the target output stream
     */
    private void printRow(PrintStream printStream, int rowIndex) {
        IntStream.range(0, columns.length).forEachOrdered(colIndex -> {
            var c = columns[colIndex];
            var paddedWidth = colIndex == columns.length - 1
                    ? c.getWidth()
                    : c.getWidth() + columnDelimiterLength;
            printStream.printf("%-" + paddedWidth + "s", c.getRow(rowIndex));
            if (colIndex == columns.length - 1)
                printStream.println();
        });
    }

    /**
     * Returns the table's formatted output as a String.
     * @return String
     */
    @Override
    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, UTF_8)) {
            print(ps);
        }
        return baos.toString();
    }

    /**
     * Verifies the table has columns, and each column has the same number of rows.
     */
    private void validateStructure() {
        if (columns.length == 0)
            throw new IllegalArgumentException("Table has no columns.");

        if (columns[0].isEmpty())
            throw new IllegalArgumentException(
                    format("Table's 1st column (%s) has no data.",
                            columns[0].getName()));

        IntStream.range(1, columns.length).forEachOrdered(colIndex -> {
            var c = columns[colIndex];

            if (c.isEmpty())
                throw new IllegalStateException(
                        format("Table column # %d (%s) does not have any data.",
                                colIndex + 1,
                                c.getName()));

            if (this.rowCount != c.rowCount())
                throw new IllegalStateException(
                        format("Table column # %d (%s) does not have same number of rows as 1st column.",
                                colIndex + 1,
                                c.getName()));
        });
    }
}

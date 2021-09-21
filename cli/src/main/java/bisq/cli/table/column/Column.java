package bisq.cli.table.column;

import java.util.List;

public interface Column<T> {

    enum JUSTIFICATION {
        LEFT,
        RIGHT,
        NONE
    }

    /**
     * Returns the column's name.
     *
     * @return name as String
     */
    String getName();

    /**
     * Sets the column name.
     *
     * @param name of the column
     */
    void setName(String name);

    /**
     * Add column value.
     *
     * @param value added to column's data (row)
     */
    void addRow(T value);

    /**
     * Returns the column data.
     *
     * @return rows as List<T>
     */
    List<T> getRows();

    /**
     * Returns the maximum width of the column name, or longest,
     * formatted string value -- whichever is greater.
     *
     * @return width of the populated column as int
     */
    int getWidth();

    /**
     * Returns the number of rows in the column.
     *
     * @return number of rows in the column as int.
     */
    int rowCount();

    /**
     * Returns true if the column has no data.
     *
     * @return true if empty, false if not
     */
    boolean isEmpty();

    /**
     * Returns the column value (data) at given row index.
     *
     * @return value object
     */
    T getRow(int rowIndex);

    /**
     * Update an existing value at the given row index to a new value.
     *
     * @param rowIndex row index of value to be updated
     * @param newValue new value
     */
    void updateRow(int rowIndex, T newValue);

    /**
     * Returns the row value as a formatted String.
     *
     * @return a row value as formatted String
     */
    String getRowAsFormattedString(int rowIndex);

    /**
     * Return the column with all of its data as a StringColumn with all of its
     * formatted string data.
     *
     * @return StringColumn
     */
    StringColumn asStringColumn();

    /**
     * Returns JUSTIFICATION value (RIGHT|LEFT|NONE) for the column.
     *
     * @return column JUSTIFICATION
     */
    JUSTIFICATION getJustification();
}

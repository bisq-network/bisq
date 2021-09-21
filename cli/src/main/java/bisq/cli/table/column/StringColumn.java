package bisq.cli.table.column;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static bisq.cli.table.column.Column.JUSTIFICATION.LEFT;

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
}

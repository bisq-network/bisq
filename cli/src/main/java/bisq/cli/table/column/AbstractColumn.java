package bisq.cli.table.column;

import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;

/**
 * Partial implementation of the {@link Column} interface.
 */
abstract class AbstractColumn<C extends Column<T>, T> implements Column<T> {

    // We create an encapsulated StringColumn up front to populate with formatted
    // strings in each this.addRow(Long value) call.  But we will not know how
    // to justify the cached, formatted string until the column is fully populated.
    protected final StringColumn stringColumn;

    // The name field is not final, so it can be re-set for column alignment.
    protected String name;
    protected final JUSTIFICATION justification;
    // The max width is not known until after column is fully populated.
    protected int maxWidth;

    public AbstractColumn(String name, JUSTIFICATION justification) {
        this.name = name;
        this.justification = justification;
        this.stringColumn = this instanceof StringColumn ? null : new StringColumn(name, justification);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getWidth() {
        return maxWidth;
    }

    @Override
    public JUSTIFICATION getJustification() {
        return this.justification;
    }

    protected final String toJustifiedString(String s) {
        switch (justification) {
            case LEFT:
                return padEnd(s, maxWidth, ' ');
            case RIGHT:
                return padStart(s, maxWidth, ' ');
            case NONE:
            default:
                return s;
        }
    }
}


package bisq.cli.table.column;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.stream.IntStream;

import static bisq.cli.table.column.Column.JUSTIFICATION.LEFT;
import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;
import static java.lang.System.currentTimeMillis;
import static java.util.TimeZone.getTimeZone;

public class Iso8601DateTimeColumn extends LongColumn {

    protected final SimpleDateFormat iso8601DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    // The default Iso8601DateTimeColumn JUSTIFICATION is LEFT.
    public Iso8601DateTimeColumn(String name) {
        this(name, LEFT);
    }

    public Iso8601DateTimeColumn(String name, JUSTIFICATION justification) {
        super(name, justification);
        iso8601DateFormat.setTimeZone(getTimeZone("UTC"));
        this.maxWidth = Math.max(name.length(), String.valueOf(currentTimeMillis()).length());
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        long time = getRow(rowIndex);
        return justification.equals(LEFT)
                ? padEnd(iso8601DateFormat.format(new Date(time)), maxWidth, ' ')
                : padStart(iso8601DateFormat.format(new Date(time)), maxWidth, ' ');
    }

    @Override
    public StringColumn asStringColumn() {
        IntStream.range(0, rows.size()).forEachOrdered(rowIndex ->
                stringColumn.addRow(getRowAsFormattedString(rowIndex)));

        return stringColumn;
    }
}

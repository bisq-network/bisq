package bisq.cli.table.column;

import java.util.stream.IntStream;

import static bisq.cli.CurrencyFormat.formatBsq;
import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;

public class SatoshiColumn extends LongColumn {

    protected final boolean isBsqSatoshis;

    // The default SatoshiColumn JUSTIFICATION is RIGHT.
    public SatoshiColumn(String name) {
        this(name, RIGHT, false);
    }

    public SatoshiColumn(String name, boolean isBsqSatoshis) {
        this(name, RIGHT, isBsqSatoshis);
    }

    public SatoshiColumn(String name, JUSTIFICATION justification) {
        this(name, justification, false);
    }

    public SatoshiColumn(String name, JUSTIFICATION justification, boolean isBsqSatoshis) {
        super(name, justification);
        this.isBsqSatoshis = isBsqSatoshis;
    }

    @Override
    public void addRow(Long value) {
        rows.add(value);

        // We do not know how much padding each StringColumn value needs until it has all the values.
        String s = isBsqSatoshis ? formatBsq(value) : formatSatoshis(value);
        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return isBsqSatoshis
                ? formatBsq(getRow(rowIndex))
                : formatSatoshis(getRow(rowIndex));
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
        return this.stringColumn;
    }
}

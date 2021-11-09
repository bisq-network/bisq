package bisq.cli.table.column;

import java.util.stream.IntStream;

import static bisq.cli.CurrencyFormat.formatBtc;
import static com.google.common.base.Strings.padEnd;
import static java.util.Comparator.comparingInt;

public class BtcColumn extends SatoshiColumn {

    public BtcColumn(String name) {
        super(name);
    }

    @Override
    public void addRow(Long value) {
        rows.add(value);

        String s = formatBtc(value);
        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return formatBtc(getRow(rowIndex));
    }

    @Override
    public StringColumn asStringColumn() {
        // We cached the formatted satoshi strings, but we did
        // not know how much zero padding each string needed until now.
        int maxColumnValueWidth = stringColumn.getRows().stream()
                .max(comparingInt(String::length))
                .get()
                .length();
        IntStream.range(0, stringColumn.getRows().size()).forEach(rowIndex -> {
            String btcString = stringColumn.getRow(rowIndex);
            if (btcString.length() < maxColumnValueWidth) {
                String paddedBtcString = padEnd(btcString, maxColumnValueWidth, '0');
                stringColumn.updateRow(rowIndex, paddedBtcString);
            }
        });
        return stringColumn.justify();
    }
}

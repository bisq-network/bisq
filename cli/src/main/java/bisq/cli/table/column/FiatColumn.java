package bisq.cli.table.column;

import java.util.stream.IntStream;

import static bisq.cli.CurrencyFormat.formatOfferVolume;
import static bisq.cli.CurrencyFormat.formatPrice;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;
import static bisq.cli.table.column.FiatColumn.DISPLAY_MODE.PRICE;
import static bisq.cli.table.column.FiatColumn.DISPLAY_MODE.TRIGGER_PRICE;

public class FiatColumn extends LongColumn {

    public enum DISPLAY_MODE {
        PRICE,
        TRIGGER_PRICE,
        VOLUME
    }

    private final DISPLAY_MODE displayMode;

    // The default FiatColumn JUSTIFICATION is RIGHT.
    // The default FiatColumn DISPLAY_MODE is PRICE.
    public FiatColumn(String name) {
        this(name, RIGHT, PRICE);
    }

    public FiatColumn(String name, DISPLAY_MODE displayMode) {
        this(name, RIGHT, displayMode);
    }

    public FiatColumn(String name, JUSTIFICATION justification) {
        this(name, justification, PRICE);
    }

    public FiatColumn(String name,
                      JUSTIFICATION justification,
                      DISPLAY_MODE displayMode) {
        super(name, justification);
        this.displayMode = displayMode;
    }

    @Override
    public void addRow(Long value) {
        rows.add(value);

        String s;
        if (displayMode.equals(TRIGGER_PRICE))
            s = value > 0 ? formatPrice(value) : "";
        else
            s = displayMode.equals(PRICE) ? formatPrice(value) : formatOfferVolume(value);

        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return getRow(rowIndex).toString();
    }

    @Override
    public StringColumn asStringColumn() {
        // We cached the formatted fiat price strings, but we did
        // not know how much padding each string needed until now.
        IntStream.range(0, stringColumn.getRows().size()).forEach(rowIndex -> {
            String unjustified = stringColumn.getRow(rowIndex);
            String justified = stringColumn.toJustifiedString(unjustified);
            stringColumn.updateRow(rowIndex, justified);
        });
        return this.stringColumn;
    }
}

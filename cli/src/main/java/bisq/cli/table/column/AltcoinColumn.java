package bisq.cli.table.column;

import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static bisq.cli.CurrencyFormat.formatCryptoCurrencyOfferVolume;
import static bisq.cli.CurrencyFormat.formatCryptoCurrencyPrice;
import static bisq.cli.table.column.AltcoinColumn.DISPLAY_MODE.ALTCOIN_PRICE;
import static bisq.cli.table.column.Column.JUSTIFICATION.RIGHT;

public class AltcoinColumn extends LongColumn {

    public enum DISPLAY_MODE {
        ALTCOIN_OFFER_VOLUME,
        ALTCOIN_PRICE,
        ALTCOIN_TRIGGER_PRICE
    }

    private final DISPLAY_MODE displayMode;

    // The default AltcoinColumn JUSTIFICATION is RIGHT.
    // The default AltcoinColumn DISPLAY_MODE is ALTCOIN_PRICE.
    public AltcoinColumn(String name) {
        this(name, RIGHT, ALTCOIN_PRICE);
    }

    public AltcoinColumn(String name, DISPLAY_MODE displayMode) {
        this(name, RIGHT, displayMode);
    }

    public AltcoinColumn(String name, JUSTIFICATION justification) {
        this(name, justification, ALTCOIN_PRICE);
    }

    public AltcoinColumn(String name,
                         JUSTIFICATION justification,
                         DISPLAY_MODE displayMode) {
        super(name, justification);
        this.displayMode = displayMode;
    }

    @Override
    public void addRow(Long value) {
        rows.add(value);

        String s = toFormattedString.apply(value, displayMode);
        stringColumn.addRow(s);

        if (isNewMaxWidth.test(s))
            maxWidth = s.length();
    }

    @Override
    public String getRowAsFormattedString(int rowIndex) {
        return toFormattedString.apply(getRow(rowIndex), displayMode);
    }

    @Override
    public StringColumn asStringColumn() {
        // We cached the formatted altcoin value strings, but we did
        // not know how much padding each string needed until now.
        IntStream.range(0, stringColumn.getRows().size()).forEach(rowIndex -> {
            String unjustified = stringColumn.getRow(rowIndex);
            String justified = stringColumn.toJustifiedString(unjustified);
            stringColumn.updateRow(rowIndex, justified);
        });
        return this.stringColumn;
    }

    private final BiFunction<Long, DISPLAY_MODE, String> toFormattedString = (value, displayMode) -> {
        switch (displayMode) {
            case ALTCOIN_OFFER_VOLUME:
                return value > 0 ? formatCryptoCurrencyOfferVolume(value) : "";
            case ALTCOIN_PRICE:
            case ALTCOIN_TRIGGER_PRICE:
                return value > 0 ? formatCryptoCurrencyPrice(value) : "";
            default:
                throw new IllegalStateException("invalid display mode: " + displayMode);
        }
    };
}

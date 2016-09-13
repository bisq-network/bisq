package io.bitsquare.gui.main.offer.createoffer.monetary;

import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MonetaryWrapper {
    private static final Logger log = LoggerFactory.getLogger(MonetaryWrapper.class);

    /// Instance of Fiat or Altcoin
    protected final Monetary monetary;
    protected final MonetaryFormat fiatFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0);
    protected final MonetaryFormat altCoinFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0);

    public MonetaryWrapper(Monetary monetary) {
        this.monetary = monetary;
    }

    public Monetary getMonetary() {
        return monetary;
    }

    public boolean isZero() {
        return monetary.getValue() == 0;
    }

    public int smallestUnitExponent() {
        return monetary.smallestUnitExponent();
    }

    public long getValue() {
        return monetary.getValue();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (o == null || o.getClass() != getClass())
            return false;
        final Monetary otherMonetary = ((MonetaryWrapper) o).getMonetary();
        if (monetary.getValue() != otherMonetary.getValue())
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return (int) monetary.getValue();
    }
}

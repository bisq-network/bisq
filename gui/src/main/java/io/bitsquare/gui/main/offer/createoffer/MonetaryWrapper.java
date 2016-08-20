package io.bitsquare.gui.main.offer.createoffer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MonetaryWrapper {
    private static final Logger log = LoggerFactory.getLogger(MonetaryWrapper.class);

    protected final Monetary monetary;
    protected final MonetaryFormat fiatFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0);

    public MonetaryWrapper(Monetary monetary) {
        this.monetary = monetary;
    }

    abstract String format();

    public Monetary getMonetary() {
        return monetary;
    }

    public boolean isZero() {
        return monetary.getValue() == 0;
    }

    public Monetary exchange(Coin coin) {
        if (monetary instanceof Fiat)
            return new ExchangeRate((Fiat) monetary).coinToFiat(coin);
        else if (monetary instanceof Altcoin)
            return new AltcoinExchangeRate((Altcoin) monetary).coinToAltcoin(coin);
        else
            throw new IllegalStateException("Monetary must be either of type Fiat or Altcoin");
    }

    public Coin exchange(Monetary convert) {
        if (convert instanceof Fiat && monetary instanceof Fiat)
            return new ExchangeRate((Fiat) monetary).fiatToCoin((Fiat) convert);
        else if (convert instanceof Altcoin && monetary instanceof Altcoin)
            return new AltcoinExchangeRate((Altcoin) monetary).altcoinToCoin((Altcoin) convert);
        else
            return Coin.ZERO;
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

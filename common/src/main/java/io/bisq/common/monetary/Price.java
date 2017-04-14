package io.bisq.common.monetary;

import io.bisq.common.locale.CurrencyUtil;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for price values with variable precision. If monetary is Altcoin we use precision 8 otherwise Fiat with precision 4.
 * The inverted price notation in the offer will be refactored once in a bigger refactoring update.
 */
public class Price extends MonetaryWrapper implements Comparable<Price> {
    private static final Logger log = LoggerFactory.getLogger(Price.class);

    public Price(Monetary monetary) {
        super(monetary);
    }

    public static Price parse(String currencyCode, String inputValue) {
        final String cleaned = inputValue.replace(",", ".");
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return new Price(Fiat.parseFiat(currencyCode, cleaned));
        else
            return new Price(Altcoin.parseAltcoin(currencyCode, cleaned));
    }

    public static Price valueOf(String currencyCode, long value) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            return new Price(Fiat.valueOf(currencyCode, value));
        } else {
            return new Price(Altcoin.valueOf(currencyCode, value));
        }
    }

    public Volume getVolumeByAmount(Coin amount) {
        if (monetary instanceof Fiat)
            return new Volume(new ExchangeRate((Fiat) monetary).coinToFiat(amount));
        else if (monetary instanceof Altcoin)
            return new Volume(new AltcoinExchangeRate((Altcoin) monetary).coinToAltcoin(amount));
        else
            throw new IllegalStateException("Monetary must be either of type Fiat or Altcoin");
    }

    public Coin getAmountByVolume(Volume volume) {
        Monetary monetary = volume.getMonetary();
        if (monetary instanceof Fiat && this.monetary instanceof Fiat)
            return new ExchangeRate((Fiat) this.monetary).fiatToCoin((Fiat) monetary);
        else if (monetary instanceof Altcoin && this.monetary instanceof Altcoin)
            return new AltcoinExchangeRate((Altcoin) this.monetary).altcoinToCoin((Altcoin) monetary);
        else
            return Coin.ZERO;
    }

    private static int getPrecision(String currencyCode) {
        return CurrencyUtil.isCryptoCurrency(currencyCode) ? 8 : 4;
    }

    public String getCurrencyCode() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).getCurrencyCode() : ((Fiat) monetary).getCurrencyCode();
    }

    public long getValue() {
        return monetary.getValue();
    }

    @Override
    public int compareTo(@NotNull Price other) {
        if (!this.getCurrencyCode().equals(other.getCurrencyCode()))
            return this.getCurrencyCode().compareTo(other.getCurrencyCode());
        if (this.getValue() != other.getValue())
            return this.getValue() > other.getValue() ? 1 : -1;
        return 0;
    }

    public boolean isPositive() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).isPositive() : ((Fiat) monetary).isPositive();
    }

    public Price subtract(Price other) {
        if (monetary instanceof Altcoin) {
            return new Price(((Altcoin) monetary).subtract((Altcoin) other.monetary));
        } else {
            return new Price(((Fiat) monetary).subtract((Fiat) other.monetary));
        }
    }

    public String toFriendlyString() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).toFriendlyString() : ((Fiat) monetary).toFriendlyString();
    }

    public String toPlainString() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).toPlainString() : ((Fiat) monetary).toPlainString();
    }

    @Override
    public String toString() {
        return toPlainString();
    }
}

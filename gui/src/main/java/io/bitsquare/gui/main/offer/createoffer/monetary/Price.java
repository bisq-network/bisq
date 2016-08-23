package io.bitsquare.gui.main.offer.createoffer.monetary;

import io.bitsquare.common.util.MathUtils;
import io.bitsquare.locale.CurrencyUtil;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

/**
 * Wrapper for price values with variable precision. If monetary is Altcoin we use precision 8 otherwise Fiat with precision 4.
 * The inverted price notation in the offer will be refactored once in a bigger refactoring update.
 */
public class Price extends MonetaryWrapper {
    private static final Logger log = LoggerFactory.getLogger(Price.class);

    private DecimalFormat decimalFormat = new DecimalFormat("#.#");

    public Price(Monetary monetary) {
        super(monetary);
        decimalFormat.setMaximumFractionDigits(smallestUnitExponent());
    }

    public static Price parse(String inputValue, String currencyCode) {
        final String cleaned = inputValue.replace(",", ".");
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            return new Price(Fiat.parseFiat(currencyCode, cleaned));
        } else {
            try {
                // roundDouble needed here to round last digit of input
                double doubleValue = MathUtils.roundDouble(Double.parseDouble(cleaned), Altcoin.SMALLEST_UNIT_EXPONENT);
                double inverted = doubleValue != 0 ? 1d / doubleValue : 0;
                double scaled = MathUtils.scaleUpByPowerOf10(inverted, Altcoin.SMALLEST_UNIT_EXPONENT);
                final long roundedToLong = MathUtils.roundDoubleToLong(scaled);
                return new Price(Altcoin.valueOf(currencyCode, roundedToLong));

            } catch (Throwable t) {
                log.warn("Exception at Price.parse: " + t.toString());
                return new Price(Altcoin.valueOf(currencyCode, 0));
            }
        }
    }

    public Monetary getVolumeByAmount(Coin amount) {
        if (monetary instanceof Fiat)
            return new ExchangeRate((Fiat) monetary).coinToFiat(amount);
        else if (monetary instanceof Altcoin)
            return new AltcoinExchangeRate((Altcoin) monetary).coinToAltcoin(amount);
        else
            throw new IllegalStateException("Monetary must be either of type Fiat or Altcoin");
    }

    public Coin getAmountByVolume(Monetary convert) {
        if (convert instanceof Fiat && monetary instanceof Fiat)
            return new ExchangeRate((Fiat) monetary).fiatToCoin((Fiat) convert);
        else if (convert instanceof Altcoin && monetary instanceof Altcoin)
            return new AltcoinExchangeRate((Altcoin) monetary).altcoinToCoin((Altcoin) convert);
        else
            return Coin.ZERO;
    }

    @Override
    public String toString() {
        if (monetary != null) {
            try {
                if (monetary instanceof Fiat)
                    return fiatFormat.noCode().format(monetary).toString();
                else if (monetary instanceof Altcoin) {
                    long longValue = ((Altcoin) monetary).value;
                    double doubleValue = longValue != 0 ? 1d / longValue : 0;
                    doubleValue = MathUtils.scaleUpByPowerOf10(doubleValue, smallestUnitExponent());
                    doubleValue = MathUtils.roundDouble(doubleValue, smallestUnitExponent());
                    return decimalFormat.format(doubleValue).replace(",", ".");
                }
            } catch (Throwable t) {
                log.warn("Exception at format: " + t.toString());
            }
        }
        return "";
    }
}

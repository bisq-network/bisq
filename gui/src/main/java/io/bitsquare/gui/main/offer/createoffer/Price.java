package io.bitsquare.gui.main.offer.createoffer;

import io.bitsquare.common.util.MathUtils;
import io.bitsquare.locale.CurrencyUtil;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

public class Price extends MonetaryWrapper {
    private static final Logger log = LoggerFactory.getLogger(Price.class);

    private DecimalFormat decimalFormat = new DecimalFormat("#.#");

    public Price(Monetary monetary) {
        super(monetary);
        decimalFormat.setMaximumFractionDigits(8);
    }

    public static Price parse(String inputValue, String currencyCode) {
        final String cleaned = inputValue.replace(",", ".");
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            return new Price(Fiat.parseFiat(currencyCode, cleaned));
        } else {
            try {
                double doubleValue = Double.parseDouble(cleaned);
                doubleValue = MathUtils.roundDouble(doubleValue, 8);
                doubleValue = doubleValue != 0 ? 1d / doubleValue : 0;
                doubleValue = MathUtils.roundDouble(doubleValue, 8);
                doubleValue = MathUtils.scaleUp(doubleValue, 8);
                long longValue = Math.round(doubleValue);
                return new Price(Altcoin.valueOf(currencyCode, longValue));
            } catch (Throwable t) {
                log.warn("Exception at Price.parse: " + t.toString());
                return new Price(Altcoin.valueOf(currencyCode, 0));
            }
        }
    }

    @Override
    public String format() {
        if (monetary != null) {
            try {
                if (monetary instanceof Fiat)
                    return fiatFormat.noCode().format(monetary).toString();
                else if (monetary instanceof Altcoin) {
                    long longValue = ((Altcoin) monetary).value;
                    double doubleValue = longValue != 0 ? 1d / longValue : 0;
                    doubleValue = MathUtils.scaleUp(doubleValue, 8);
                    doubleValue = MathUtils.roundDouble(doubleValue, 8);
                    return decimalFormat.format(doubleValue).replace(",", ".");
                }
            } catch (Throwable t) {
                log.warn("Exception at format: " + t.toString());
            }
        }
        return "";
    }
}

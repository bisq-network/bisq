package io.bisq.common.monetary;

import io.bisq.common.locale.CurrencyUtil;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Volume extends MonetaryWrapper implements Comparable<Volume> {
    private static final Logger log = LoggerFactory.getLogger(Volume.class);

    public Volume(Monetary monetary) {
        super(monetary);
    }

    public static Volume parse(String inputValue, String currencyCode) {
        final String cleaned = inputValue.replace(",", ".");
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return new Volume(Fiat.parseFiat(currencyCode, cleaned));
        else
            return new Volume(Altcoin.parseAltcoin(currencyCode, cleaned));
    }

    @Override
    public int compareTo(@NotNull Volume other) {
        if (!this.getCurrencyCode().equals(other.getCurrencyCode()))
            return this.getCurrencyCode().compareTo(other.getCurrencyCode());
        if (this.getValue() != other.getValue())
            return this.getValue() > other.getValue() ? 1 : -1;
        return 0;
    }

    public String getCurrencyCode() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).getCurrencyCode() : ((Fiat) monetary).getCurrencyCode();
    }

    public String toPlainString() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).toPlainString() : ((Fiat) monetary).toPlainString();
    }

    @Override
    public String toString() {
        return toPlainString();
    }
}

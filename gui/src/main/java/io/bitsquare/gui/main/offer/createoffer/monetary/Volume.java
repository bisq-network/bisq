package io.bitsquare.gui.main.offer.createoffer.monetary;

import io.bitsquare.locale.CurrencyUtil;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Volume extends MonetaryWrapper {
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
    public String toString() {
        if (monetary != null) {
            try {
                if (monetary instanceof Fiat)
                    return fiatFormat.noCode().format(monetary).toString();
                else if (monetary instanceof Altcoin)
                    return altCoinFormat.noCode().format(monetary).toString();

            } catch (Throwable t) {
                log.warn("Exception at format: " + t.toString());
            }
        }
        return "";
    }
}

package io.bisq.gui.util;

import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import javafx.util.StringConverter;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static io.bisq.common.locale.TradeCurrencyMakers.bitcoin;
import static io.bisq.common.locale.TradeCurrencyMakers.euro;
import static io.bisq.core.user.PreferenceMakers.empty;
import static org.junit.Assert.assertEquals;

public class GUIUtilTest {

    @Before
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
    }

    @Test
    public void testTradeCurrencyConverter() {
        StringConverter<TradeCurrency> tradeCurrencyConverter = GUIUtil.getTradeCurrencyConverter();

        assertEquals("✦ BTC (BTC)", tradeCurrencyConverter.toString(bitcoin));
        assertEquals("★ Euro (EUR)", tradeCurrencyConverter.toString(euro));
    }

    @Test
    public void testCurrencyListWithOffersConverter() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
        StringConverter<CurrencyListItem> currencyListItemConverter = GUIUtil.getCurrencyListItemConverter(Res.get("shared.offer"),
                Res.get("shared.offers"),
                empty);

        assertEquals("✦ BTC (BTC) - 10 offers", currencyListItemConverter.toString(new CurrencyListItem(bitcoin,10)));

    }
}

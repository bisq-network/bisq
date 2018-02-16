package io.bisq.gui.util;

import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import javafx.util.StringConverter;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static io.bisq.common.locale.TradeCurrencyMakers.bitcoin;
import static io.bisq.common.locale.TradeCurrencyMakers.euro;
import static io.bisq.core.user.PreferenceMakers.empty;
import static io.bisq.gui.util.CurrencyListItemMakers.*;
import static org.junit.Assert.assertEquals;

public class GUIUtilTest {

    @Before
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
    }

    @Test
    public void testTradeCurrencyConverter() {
        Map<String, Integer> offerCounts = new HashMap<String, Integer>() {{
            put("BTC", 11);
            put("EUR", 10);
        }};
        StringConverter<TradeCurrency> tradeCurrencyConverter = GUIUtil.getTradeCurrencyConverter(
                Res.get("shared.offer"),
                Res.get("shared.offers"),
                offerCounts
        );

        assertEquals("✦ BTC (BTC) - 11 offers", tradeCurrencyConverter.toString(bitcoin));
        assertEquals("★ Euro (EUR) - 10 offers", tradeCurrencyConverter.toString(euro));
    }

    @Test
    public void testCurrencyListWithOffersConverter() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
        StringConverter<CurrencyListItem> currencyListItemConverter = GUIUtil.getCurrencyListItemConverter(Res.get("shared.offer"),
                Res.get("shared.offers"),
                empty);

        assertEquals("✦ BTC (BTC) - 10 offers", currencyListItemConverter.toString(make(bitcoinItem.but(with(numberOfTrades,10)))));
        assertEquals("★ Euro (EUR) - 0 offers", currencyListItemConverter.toString(make(euroItem)));
        assertEquals("★ Euro (EUR) - 1 offer", currencyListItemConverter.toString(make(euroItem.but(with(numberOfTrades, 1)))));

    }
}

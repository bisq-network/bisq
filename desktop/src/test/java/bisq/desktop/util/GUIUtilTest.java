/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util;

import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;

import javafx.util.StringConverter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static bisq.core.locale.TradeCurrencyMakers.bitcoin;
import static bisq.core.locale.TradeCurrencyMakers.euro;
import static bisq.core.user.PreferenceMakers.empty;
import static bisq.desktop.util.CurrencyListItemMakers.bitcoinItem;
import static bisq.desktop.util.CurrencyListItemMakers.euroItem;
import static bisq.desktop.util.CurrencyListItemMakers.numberOfTrades;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.Assert.assertEquals;

public class GUIUtilTest {

    @Before
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
        GlobalSettings.setLocale(new Locale("en", "US"));
    }

    @Test
    public void testTradeCurrencyConverter() {
        Map<String, Integer> offerCounts = new HashMap<String, Integer>() {{
            put("BTC", 11);
            put("EUR", 10);
        }};
        StringConverter<TradeCurrency> tradeCurrencyConverter = GUIUtil.getTradeCurrencyConverter(
                Res.get("shared.oneOffer"),
                Res.get("shared.multipleOffers"),
                offerCounts
        );

        assertEquals("✦ BTC (BTC) - 11 offers", tradeCurrencyConverter.toString(bitcoin));
        assertEquals("★ Euro (EUR) - 10 offers", tradeCurrencyConverter.toString(euro));
    }

    @Test
    public void testCurrencyListWithOffersConverter() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
        StringConverter<CurrencyListItem> currencyListItemConverter = GUIUtil.getCurrencyListItemConverter(Res.get("shared.oneOffer"),
                Res.get("shared.multipleOffers"),
                empty);

        assertEquals("✦ BTC (BTC) - 10 offers", currencyListItemConverter.toString(make(bitcoinItem.but(with(numberOfTrades, 10)))));
        assertEquals("★ Euro (EUR) - 0 offers", currencyListItemConverter.toString(make(euroItem)));
        assertEquals("★ Euro (EUR) - 1 offer", currencyListItemConverter.toString(make(euroItem.but(with(numberOfTrades, 1)))));

    }
}

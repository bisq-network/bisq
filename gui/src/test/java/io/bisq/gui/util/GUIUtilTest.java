package io.bisq.gui.util;

import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static io.bisq.common.locale.TradeCurrencyMakers.bitcoin;
import static io.bisq.common.locale.TradeCurrencyMakers.euro;
import static io.bisq.core.user.PreferenceMakers.empty;
import static io.bisq.gui.util.CurrencyListItemMakers.bitcoinItem;
import static io.bisq.gui.util.CurrencyListItemMakers.euroItem;
import static io.bisq.gui.util.CurrencyListItemMakers.numberOfTrades;
import static org.junit.Assert.*;

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

        assertEquals("✦ BTC (BTC) - 10 offers", currencyListItemConverter.toString(make(bitcoinItem.but(with(numberOfTrades,10)))));
        assertEquals("★ Euro (EUR) - 0 offers", currencyListItemConverter.toString(make(euroItem)));
        assertEquals("★ Euro (EUR) - 1 offer", currencyListItemConverter.toString(make(euroItem.but(with(numberOfTrades, 1)))));

    }

    @Test
    public void testFillCurrencyListItems() {
        ObservableList<CurrencyListItem> currencyListItems = FXCollections.observableArrayList();
        List<TradeCurrency> tradeCurrencyList = new ArrayList<>();
        tradeCurrencyList.add(euro);
        CurrencyListItem euroListItem = make(euroItem.but(with(numberOfTrades,1)));

        GUIUtil.fillCurrencyListItems(tradeCurrencyList, currencyListItems, null, empty );
        assertTrue(euroListItem.equals(currencyListItems.get(0)));
    }
}

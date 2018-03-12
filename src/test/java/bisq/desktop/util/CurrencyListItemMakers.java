package bisq.desktop.util;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;
import bisq.common.locale.TradeCurrency;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static bisq.common.locale.TradeCurrencyMakers.bitcoin;
import static bisq.common.locale.TradeCurrencyMakers.euro;

public class CurrencyListItemMakers {

    public static final Property<CurrencyListItem, TradeCurrency> tradeCurrency = new Property<>();
    public static final Property<CurrencyListItem, Integer> numberOfTrades = new Property<>();

    public static final Instantiator<CurrencyListItem> CurrencyListItem = lookup ->
         new CurrencyListItem(lookup.valueOf(tradeCurrency, bitcoin), lookup.valueOf(numberOfTrades, 0));

    public static final Maker<CurrencyListItem> bitcoinItem = a(CurrencyListItem);
    public static final Maker<CurrencyListItem> euroItem = a(CurrencyListItem, with(tradeCurrency, euro));
}

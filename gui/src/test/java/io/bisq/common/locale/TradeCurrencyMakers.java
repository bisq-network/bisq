package io.bisq.common.locale;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Property;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;

public class TradeCurrencyMakers {

    public static final Property<TradeCurrency, String> currencyCode = new Property<>();
    public static final Property<TradeCurrency, String> currencyName = new Property<>();

    public static final Instantiator<CryptoCurrency> CryptoCurrency = lookup ->
            new CryptoCurrency(lookup.valueOf(currencyCode, "BTC"), lookup.valueOf(currencyName, "Bitcoin"));

    public static final Instantiator<FiatCurrency> FiatCurrency = lookup ->
            new FiatCurrency(lookup.valueOf(currencyCode, "EUR"));

    public static final CryptoCurrency bitcoin = make(a(CryptoCurrency));
    public static final FiatCurrency euro = make(a(FiatCurrency));
}


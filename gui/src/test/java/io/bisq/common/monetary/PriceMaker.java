package io.bisq.common.monetary;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;
import org.bitcoinj.utils.Fiat;

import static com.natpryce.makeiteasy.MakeItEasy.a;

public class PriceMaker {

    public static final Property<Price, String> currencyCode = new Property<>();
    public static final Property<Price, String> priceString = new Property<>();

    public static final Instantiator<Price> FiatPrice = lookup ->
            new Price(Fiat.parseFiat(lookup.valueOf(currencyCode, "USD"), lookup.valueOf(priceString, "100")));

    public static final Instantiator<Price> AltcoinPrice = lookup ->
            new Price(Altcoin.parseAltcoin(lookup.valueOf(currencyCode, "LTC"), lookup.valueOf(priceString, "100")));

    public static final Maker<Price> usdPrice = a(FiatPrice);
    public static final Maker<Price> ltcPrice = a(AltcoinPrice);
}

package bisq.httpapi;

import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;

import bisq.httpapi.model.PriceFeed;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static java.util.stream.Collectors.toList;

//TODO @bernard we need ot break that apart to smaller domain specific chunks (or then use core domains directly).
// its very hard atm to get an overview here

/**
 * This class is a proxy for all Bisq features the model will use.
 * <p>
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 * => this should be the common gateway to bisq used by all outward-facing API classes.
 * <p>
 * If the bisq code is refactored correctly, this class could become very light.
 */
@Slf4j
public class BisqProxy {
    private final bisq.core.user.Preferences preferences;
    private final PriceFeedService priceFeedService;

    @Inject
    public BisqProxy(bisq.core.user.Preferences preferences,
                     PriceFeedService priceFeedService) {
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
    }


    /// START TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////


    /// STOP TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////

    public PriceFeed getPriceFeed(String[] codes) {
        final List<FiatCurrency> fiatCurrencies = preferences.getFiatCurrencies();
        final List<CryptoCurrency> cryptoCurrencies = preferences.getCryptoCurrencies();
        final Stream<String> codesStream;
        if (null == codes || 0 == codes.length)
            codesStream = Stream.concat(fiatCurrencies.stream(), cryptoCurrencies.stream()).map(TradeCurrency::getCode);
        else
            codesStream = Arrays.asList(codes).stream();
        final List<MarketPrice> marketPrices = codesStream
                .map(priceFeedService::getMarketPrice)
                .filter(i -> null != i)
                .collect(toList());
        final PriceFeed priceFeed = new PriceFeed();
        for (MarketPrice price : marketPrices)
            priceFeed.prices.put(price.getCurrencyCode(), price.getPrice());
        return priceFeed;
    }
}

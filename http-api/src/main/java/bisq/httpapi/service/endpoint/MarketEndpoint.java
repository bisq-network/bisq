package bisq.httpapi.service.endpoint;

import bisq.core.locale.CurrencyUtil;

import bisq.httpapi.model.CurrencyList;
import bisq.httpapi.model.Market;
import bisq.httpapi.model.MarketList;
import bisq.httpapi.service.ExperimentalFeature;

import javax.inject.Inject;

import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "markets", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class MarketEndpoint {
    private static MarketList marketList;
    private static CurrencyList currencyList;

    private final ExperimentalFeature experimentalFeature;

    @Inject
    public MarketEndpoint(ExperimentalFeature experimentalFeature) {
        this.experimentalFeature = experimentalFeature;
    }

    @ApiOperation(value = "List markets", notes = ExperimentalFeature.NOTE)
    @GET
    public MarketList find() {
        experimentalFeature.assertEnabled();
        return getMarketList();
    }

    public static MarketList getMarketList() {
        if (marketList == null) {
            marketList = new MarketList();
            CurrencyList currencyList = getCurrencyList(); // we calculate this twice but only at startup
            //currencyList.getCurrencies().stream().flatMap(currency -> marketList.getMarkets().forEach(currency1 -> cur))
            List<Market> list = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .filter(cryptoCurrency -> !(cryptoCurrency.getCode().equals("BTC")))
                    .map(cryptoCurrency -> new Market(cryptoCurrency.getCode(), "BTC"))
                    .collect(toList());
            marketList.markets.addAll(list);
            list = CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(cryptoCurrency -> new Market("BTC", cryptoCurrency.getCode()))
                    .collect(toList());
            marketList.markets.addAll(list);
            currencyList.currencies.sort(Comparator.comparing(currency -> currency.name));
        }
        return marketList;
    }

    public static CurrencyList getCurrencyList() {
        if (currencyList == null) {
            currencyList = new CurrencyList();
            CurrencyUtil.getAllSortedCryptoCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getCode(), cryptoCurrency.getName(), "crypto"));
            CurrencyUtil.getAllSortedFiatCurrencies().forEach(fiatCurrency -> currencyList.add(fiatCurrency.getCurrency().getCurrencyCode(), fiatCurrency.getName(), "fiat"));
            currencyList.currencies.sort(Comparator.comparing(currency -> currency.name));
        }
        return currencyList;
    }

    public static boolean isMarketPriceAvailable() {
        //TODO check if we have a live market price
        return true;
    }
}

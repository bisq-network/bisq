package io.bitsquare.api;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.storage.JsonString;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.statistics.TradeStatistics;
import io.bitsquare.trade.statistics.TradeStatisticsManager;
import javafx.collections.ObservableSet;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ApiResource {
    private final String template;
    private final String defaultName;
    private final AtomicLong counter;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final CurrencyList currencyList = new CurrencyList();

    public ApiResource(String template, String defaultName, TradeStatisticsManager manager) {
        this.template = template;
        this.defaultName = defaultName;
        this.counter = new AtomicLong();
        this.tradeStatisticsManager = manager;
        calculateCurrencyList();
    }

    @GET
    @Timed
    @Path("/currency_list")
    public CurrencyList currencyList() {

        HashSet<TradeStatistics> tradeStatisticsSet = tradeStatisticsManager.getTradeStatisticsSet();
//        tradeStatisticsSet.stream().map(tradeStatistics -> tradeStatistics.toString()).collect(Collectors.joining())

        return currencyList;
    }

    @GET
    @Timed
    @Path("/market_list")
    public CurrencyList marketList() {


        return currencyList;
    }

    private void calculateCurrencyList() {
        CurrencyUtil.getAllSortedCryptoCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getCode(), cryptoCurrency.getName(), "crypto"));
        CurrencyUtil.getAllSortedFiatCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getSymbol(), cryptoCurrency.getName(), "fiat"));
        Collections.sort(currencyList.currencies, (Currency p1, Currency p2) -> p1.name.compareTo(p2.name));
    }
}

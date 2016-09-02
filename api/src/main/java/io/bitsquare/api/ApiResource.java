package io.bitsquare.api;

import com.codahale.metrics.annotation.Timed;
import io.bitsquare.trade.statistics.TradeStatistics;
import io.bitsquare.trade.statistics.TradeStatisticsManager;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ApiResource {
    private final String template;
    private final String defaultName;
    private final AtomicLong counter;
    private final CurrencyList currencyList;
    private final MarketList marketList;
    private final BitsquareProxy bitsquareProxy;

    public ApiResource(String template, String defaultName, BitsquareProxy bitsquareProxy) {
        this.template = template;
        this.defaultName = defaultName;
        this.counter = new AtomicLong();
        this.bitsquareProxy = bitsquareProxy;
        currencyList = bitsquareProxy.getCurrencyList();
        marketList  = bitsquareProxy.getMarketList();
    }

    @GET
    @Timed
    @Path("/currency_list")
    public CurrencyList currencyList() {
        return currencyList;
    }

    /**
     Markets

     market_list

     Returns list of all markets

     Params

     None
     Example Return

     [ { "pair": "dash_btc", "lsymbol": "DASH", "rsymbol": "BTC", }, ... ]

     */
    @GET
    @Timed
    @Path("/market_list")
    public MarketList marketList() {
        return marketList;
    }

    /**
     * wallet_detail

     Returns wallet info. balance, etc.

     Params

     None
     Example Return

     {
     "balance": 0.5623,
     // TBD
     }
     */
    @GET
    @Timed
    @Path("/wallet_details")
    public WalletDetails walletDetail() {
        return new WalletDetails(bitsquareProxy.getWalletDetails());
    }

}

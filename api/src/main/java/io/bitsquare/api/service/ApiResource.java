package io.bitsquare.api.service;

import com.codahale.metrics.annotation.Timed;
import io.bitsquare.api.BitsquareProxy;
import io.bitsquare.api.api.AccountList;
import io.bitsquare.api.api.CurrencyList;
import io.bitsquare.api.api.MarketList;
import io.bitsquare.api.api.WalletDetails;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
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
    // "0x7fffffff";
    private static final String STRING_END_MAX_VALUE = "2147483647";

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

    /**
     * wallet_tx_list

     Returns list of wallet transactions according to criteria

     Param	Type	Required?	Default	Description
     start	timestamp	no	0	start of period
     end	timestamp	no	INT_MAX	end of period
     limit	int	no	100	maximum records to return.
     Example Return

     {
     "amount": 1.3453,
     "type": "send",
     "address": "14w4mZx4b6JjtEd9BZPnLCSXzbHjKH3Pn3",
     "time": <timestamp>,
     "confirmations": 5
     // TBD
     }
     */
    @GET
    @Timed
    @Path("/wallet_tx_list")
    public WalletDetails walletTransactionList(@DefaultValue("0") @QueryParam("start") Integer start,
                                               @DefaultValue(STRING_END_MAX_VALUE) @QueryParam("end") Integer end,
                                               @DefaultValue("100") @QueryParam("start") Integer limit
                                               ) {
        return new WalletDetails(bitsquareProxy.getWalletDetails());
    }

    @GET
    @Timed
    @Path("/account_list")
    public AccountList accountList() {
        return bitsquareProxy.getAccountList();
    }


}

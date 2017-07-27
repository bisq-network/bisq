package io.bisq.api.service;

import com.codahale.metrics.annotation.Timed;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.bisq.api.BisqProxy;
import io.bisq.api.model.*;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.trade.Trade;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Api(value = "api")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
/**
 * REST interface. This class is Dropwizard-specific.
 * Bisq logic is kept to a minimum here, everything is passed to the BisqProxy.
 */
public class ApiResourceV1 {
    private final String template;
    private final String defaultName;
    private final AtomicLong counter;
    private final CurrencyList currencyList;
    private final MarketList marketList;
    private final BisqProxy bisqProxy;
    // Needs to be a hard-coded value, otherwise annotations complain. "0x7fffffff";
    private static final String STRING_END_INT_MAX_VALUE = "2147483647";

    public ApiResourceV1(String template, String defaultName, BisqProxy bisqProxy) {
        this.template = template;
        this.defaultName = defaultName;
        this.counter = new AtomicLong();
        this.bisqProxy = bisqProxy;
        currencyList = bisqProxy.getCurrencyList();
        marketList = bisqProxy.getMarketList();
    }

    ///////////////// ACCOUNT ///////////////////////////

    @GET
    @Timed
    @Path("/account_list")
    public AccountList accountList() {
        return bisqProxy.getAccountList();
    }

    ///////////////// CURRENCY ///////////////////////////

    @GET
    @Timed
    @Path("/currency_list")
    // @CacheControl(maxAge=6, maxAgeUnit = TimeUnit.HOURS)
    public CurrencyList currencyList() {
        return currencyList;
    }

    ///////////////// MARKET ///////////////////////////

    /**
     * Markets
     * <p>
     * market_list
     * <p>
     * Returns list of all markets
     * <p>
     * Params
     * <p>
     * None
     * Example Return
     * <p>
     * [ { "pair": "dash_btc", "lsymbol": "DASH", "rsymbol": "BTC", }, ... ]
     */
    @GET
    @Timed
    @Path("/market_list")
    public MarketList marketList() {
        return marketList;
    }


    ///////////////// OFFER ///////////////////////////

    @DELETE
    @Timed
    @Path("/offer_cancel")
    public boolean offerCancel(@QueryParam("offer_id") String offerId) throws Exception {
        return bisqProxy.offerCancel(offerId);
    }

    @GET
    @Timed
    @Path("/offer_detail")
    public OfferDetail offerDetail(@QueryParam("offer_id") String offerId) throws Exception {
        return bisqProxy.getOfferDetail(offerId);
    }

    /**
     * param	type	desc	                        values	                                            default
     * market	string	filter by market		        | "all"	                                            all
     * status	string	filter by status		        "unfunded" | "live" | "done" | "cancelled" | "all"	all
     * whose	string	filter by offer creator		    "mine" | "notmine" | "all"	                        all
     * start	longint	find offers after start time. seconds since 1970.			                        0
     * end	    longint	find offers before end time. seconds since 1970.			                        9223372036854775807
     * limit	int	    max records to return			                                                    100
     */
    @GET
    @Timed
    @Path("/offer_list")
    public List<OfferDetail> offerList(@DefaultValue("all") @QueryParam("market") String market,
                                       @DefaultValue("all") @QueryParam("status") String status,
                                       @DefaultValue("all") @QueryParam("whose") String whose,
                                       @DefaultValue("0") @QueryParam("start") long start,
                                       @DefaultValue("9223372036854775807") @QueryParam("end") long end,
                                       @DefaultValue("100") @QueryParam("limit") int limit
    ) {
        return bisqProxy.getOfferList();
    }

    /**
     * param	    type	desc	                                                                        required	values	            default
     * market	    string	identifies the market this offer will be placed in	                            1
     * payment_account_id	string	identifies the account to which funds will be received once offer is executed.	1
     * direction	string	defines if this is an offer to buy or sell	                                    1	        sell | buy
     * amount	    real	amount to buy or sell, in terms of left side of market pair	                    1
     * min_amount	real	minimum amount to buy or sell, in terms of left side of market pair	            1
     * price_type	string	defines if this is a fixed offer or a percentage offset from present market price.		    fixed | percentage	fixed
     * price	    string	interpreted according to "price-type". Percentages should be expressed in
     * decimal form eg 1/2 of 1% = "0.005" and must be positive	                                            1
     */
    @GET
    @Timed
    @Path("/offer_make")
    public boolean offerMake(@QueryParam("market") String market,
                             @NotEmpty @QueryParam("payment_account_id") String accountId,
                             @NotNull @QueryParam("direction") OfferPayload.Direction direction,
                             @NotNull @QueryParam("amount") BigDecimal amount,
                             @NotNull @QueryParam("min_amount") BigDecimal minAmount,
                             @DefaultValue("fixed") @QueryParam("price_type") String fixed,
                             @NotEmpty @QueryParam("price") String price) {
        return bisqProxy.offerMake(market, accountId, direction, amount, minAmount, false, 100, "XMR", price, "100");
    }

    /**
     * param	        type	desc	                                                required	values	default
     * offer_id	    string	Identifies the offer to accept	                        1
     * payment_account_id	string	Identifies the payment account to receive funds into	1
     * amount	    string	amount to spend	                                        1
     */
    @GET
    @Timed
    @Path("/offer_take")
    public boolean offerTake(@NotEmpty @QueryParam("offer_id") String offerId,
                             @NotEmpty @QueryParam("payment_account_id") String paymentAccountId,
                             @NotEmpty @QueryParam("amount") String amount,
                             @NotNull @QueryParam("use_savings_wallet") boolean useSavingsWallet) throws Exception {
        return bisqProxy.offerTake(offerId, paymentAccountId, amount, useSavingsWallet);
    }


    ///////////////// TRADE ///////////////////////////

    // trade_detail
    @GET
    @Timed
    @Path("/trade_detail")
    public String tradeDetail(@QueryParam("trade_id") String tradeId) {
        try {
            Optional<Trade> any = bisqProxy.getTrade(tradeId);
            if (any.isPresent())
                return JsonFormat.printer().print(any.get().toProtoMessage());
            else
                throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (InvalidProtocolBufferException e) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }


    // trade_list
    @GET
    @Timed
    @Path("/trade_list")
    public String tradeList() throws InvalidProtocolBufferException {
        return bisqProxy.getTradeList().trades.stream().map(trade -> trade.toProtoMessage()).map(message -> {
            try {
                return JsonFormat.printer().print(message);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            return "error";
        }).collect(Collectors.joining(", "));
    };

    @GET
    @Timed
    @Path("/payment_started")
    public boolean paymentStarted(@NotEmpty @QueryParam("trade_id") String tradeId) {
        if (!bisqProxy.paymentStarted(tradeId)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return true; // TODO return json
    }

    @GET
    @Timed
    @Path("/payment_received")
    public boolean paymentReceived(@NotEmpty @QueryParam("trade_id") String tradeId) {
        if (!bisqProxy.paymentReceived(tradeId)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return true; // TODO return json
    }


    ///////////////// WALLET ///////////////////////////

    @GET
    @Timed
    @Path("/move_funds_to_bisq_wallet")
    public boolean moveFundsToBisqWallet(@NotEmpty @QueryParam("trade_id") String tradeId) {
        if (!bisqProxy.moveFundsToBisqWallet(tradeId)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return true; // TODO return json
    }


    /**
     * wallet_detail
     * <p>
     * Returns wallet info. balance, etc.
     * <p>
     * Params
     * <p>
     * None
     * Example Return
     * <p>
     * {
     * "balance": 0.5623,
     * // TBD
     * }
     */
    @GET
    @Timed
    @Path("/wallet_detail")
    public WalletDetails walletDetail() {
        return bisqProxy.getWalletDetails();
    }

    /**
     * param	type	desc	required	values	default
     * status	string	filter by wether each address has a non-zero balance or not		funded | unfunded | both	both
     * start	int	starting index, zero based			0
     * limit	int	max number of addresses to return.			100
     *
     * @return
     */
    @GET
    @Timed
    @Path("/wallet_addresses")
    public List<WalletAddress> walletAddresses(@DefaultValue("BOTH") @QueryParam("status") String status,
                                               @DefaultValue("0") @QueryParam("start") Integer start,
                                               @DefaultValue("100") @QueryParam("limit") Integer limit) {
        return bisqProxy.getWalletAddresses();
    }

    /**
     * wallet_tx_list
     * <p>
     * Returns list of wallet transactions according to criteria
     * <p>
     * Param	Type	Required?	Default	Description
     * start	timestamp	no	0	start of period
     * end	timestamp	no	INT_MAX	end of period
     * limit	int	no	100	maximum records to return.
     * Example Return
     * <p>
     * {
     * "amount": 1.3453,
     * "type": "send",
     * "address": "14w4mZx4b6JjtEd9BZPnLCSXzbHjKH3Pn3",
     * "time": <timestamp>,
     * "confirmations": 5
     * // TBD
     * }
     */
    @GET
    @Timed
    @Path("/wallet_tx_list")
    public WalletTransactions walletTransactionList(@DefaultValue("0") @QueryParam("start") Integer start,
                                                    @DefaultValue(STRING_END_INT_MAX_VALUE) @QueryParam("end") Integer end,
                                                    @DefaultValue("100") @QueryParam("start") Integer limit
    ) {
//        return bisqProxy.getWalletTransactions(start, end, limit);
        return null;
    }

    ////////////////////////////// helper methods

    protected Trade getTrade(String tradeId) {
        Optional<Trade> any = bisqProxy.getTrade(tradeId);
        if (!any.isPresent())
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        return any.get();
    }

}

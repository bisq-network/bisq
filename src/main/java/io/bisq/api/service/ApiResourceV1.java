package io.bisq.api.service;

import com.codahale.metrics.annotation.Timed;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.bisq.api.BisqProxy;
import io.bisq.api.BisqProxyError;
import io.bisq.api.model.*;
import io.bisq.common.util.Tuple2;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.trade.Trade;
import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Api(value = "bisq")
@SwaggerDefinition(
        host = "http://localhost:8080",
        info = @Info(
                description = "API for the Bisq exchange",
                title = "The Bisq API",
                contact = @Contact(name = "the Bisq open source project", email = "Use the Bisq's project support channels"
                ),
                license = @License(
                        name = "GNU General Public License v3.0",
                        url = "https://www.gnu.org/licenses/gpl-3.0.en.html"
                ),
                version = "1"
        )
)
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j

/**
 * REST interface. This class is Dropwizard-specific.
 * Bisq logic is kept to a minimum here, everything is passed to the BisqProxy.
 */
public class ApiResourceV1 {
    // Needs to be a hard-coded value, otherwise annotations complain. "0x7fffffff";
    private static final String STRING_END_INT_MAX_VALUE = "2147483647";
    private final String defaultName;
    private final AtomicLong counter;
    private final BisqProxy bisqProxy;

    public ApiResourceV1(String defaultName, BisqProxy bisqProxy) {
        this.defaultName = defaultName;
        this.counter = new AtomicLong();
        this.bisqProxy = bisqProxy;
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
        return bisqProxy.getCurrencyList();
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
        return bisqProxy.getMarketList();
    }


    ///////////////// OFFER ///////////////////////////

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
                                       @Range(min = 0, max = 9223372036854775807L) @DefaultValue("9223372036854775807") @QueryParam("end") long end,
                                       @DefaultValue("100") @QueryParam("limit") int limit
    ) {
        return bisqProxy.getOfferList();
    }

    @GET
    @Timed
    @Path("/offer_detail")
    public OfferDetail offerDetail(@QueryParam("offer_id") String offerId) throws Exception {
        Tuple2<Optional<OfferDetail>, Optional<BisqProxyError>> result = bisqProxy.getOfferDetail(offerId);
        if (!result.first.isPresent()) {
            handleBisqProxyError(result.second);
        }

        return result.first.get();
    }

    @DELETE
    @Timed
    @Path("/offer_cancel")
    public boolean offerCancel(@NotEmpty @QueryParam("offer_id") String offerId) {
        return handleBisqProxyError(bisqProxy.offerCancel(offerId), Response.Status.NOT_FOUND);
    }

    /**
     * param	    type	desc	                                                                        required	values	            default
     * payment_account_id	string	identifies the account to which funds will be received once offer is executed.	1
     * direction	string	defines if this is an offer to buy or sell	                                    1	        sell | buy
     * market_pair	    string	identifies the market this offer will be placed in	                            1
     * amount	    real	amount to buy or sell, in terms of left side of market pair	                    1
     * min_amount	real	minimum amount to buy or sell, in terms of left side of market pair	            1
     * price_type	string	defines if this is a fixed offer or a percentage offset from present market price.		    fixed | percentage	fixed
     * price	    string	interpreted according to "price-type". Percentages should be expressed in
     * decimal form eg 1/2 of 1% = "0.005" and must be positive	                                            1
     */
    @GET
    @Timed
    @Path("/offer_make")
    public boolean offerMake(
            @NotEmpty @QueryParam("payment_account_id") String accountId,
            @NotNull @QueryParam("direction") OfferPayload.Direction direction,
            @NotNull @DefaultValue("FIXED") @QueryParam("price_type") PriceType priceType,
            @NotNull @QueryParam("market_pair") String marketPair,
            @Min(-1) @Max(1) @DefaultValue("0") @QueryParam("percentage_from_market_price") Double percentage_from_market_price,
            @DefaultValue("0") @QueryParam("fixed_price") String fixedPrice,
            @Min(100000) @Max(200000000) @NotNull @QueryParam("amount") BigDecimal amount,
            @Min(100000) @Max(200000000) @NotNull @QueryParam("min_amount") BigDecimal minAmount
    ) {
        return handleBisqProxyError(bisqProxy.offerMake(accountId, direction, amount, minAmount,
                PriceType.PERCENTAGE.equals(priceType), percentage_from_market_price, marketPair, fixedPrice));
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
                             @NotEmpty @QueryParam("amount") String amount) {
        //@NotNull @QueryParam("use_savings_wallet") boolean useSavingsWallet) {
        return handleBisqProxyError(bisqProxy.offerTake(offerId, paymentAccountId, amount, true));
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
    public String tradeList() {
        String result = "[]";
        TradeList tradeList = bisqProxy.getTradeList();
        if (tradeList == null || tradeList.trades == null || tradeList.trades.size() == 0) {
            // will use default result
        } else {
            try {
                List<String> stringList = tradeList.trades.stream().map(trade -> trade.toProtoMessage()).map(message -> {
                    try {
                        return JsonFormat.printer().print(message);
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                    return "error";
                }).collect(Collectors.toList());
                StringJoiner stringJoiner = new StringJoiner(",", "[", "]");
                for (String string : stringList) {
                    stringJoiner.add(string);
                }
                result = stringJoiner.toString();
            } catch (Throwable e) {
                log.error("Error processing tradeList method", e);
                // will use default result
            }
        }

        return result;
    }

    @GET
    @Timed
    @Path("/payment_started")
    public boolean paymentStarted(@NotEmpty @QueryParam("trade_id") String tradeId) {
        return handleBisqProxyError(bisqProxy.paymentStarted(tradeId), Response.Status.NOT_FOUND);
    }

    @GET
    @Timed
    @Path("/payment_received")
    public boolean paymentReceived(@NotEmpty @QueryParam("trade_id") String tradeId) {
        return handleBisqProxyError(bisqProxy.paymentReceived(tradeId), Response.Status.NOT_FOUND);
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
                                                    @DefaultValue("100") @QueryParam("limit") Integer limit
    ) {
        return bisqProxy.getWalletTransactions(start, end, limit);
    }

    ////////////////////////////// helper methods

    protected Trade getTrade(String tradeId) {
        Optional<Trade> any = bisqProxy.getTrade(tradeId);
        if (!any.isPresent())
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        return any.get();
    }

    private boolean handleBisqProxyError(Optional<BisqProxyError> optionalBisqProxyError, Response.Status status) {

        if (optionalBisqProxyError.isPresent()) {
            BisqProxyError bisqProxyError = optionalBisqProxyError.get();
            if (bisqProxyError.getOptionalThrowable().isPresent()) {
                throw new WebApplicationException(bisqProxyError.getErrorMessage(), bisqProxyError.getOptionalThrowable().get());
            } else {
                throw new WebApplicationException(bisqProxyError.getErrorMessage());
            }
        } else if (optionalBisqProxyError == null) {
            throw new WebApplicationException("Unknow error.");
        }

        return true;
    }

    private boolean handleBisqProxyError(Optional<BisqProxyError> optionalBisqProxyError) {
        return handleBisqProxyError(optionalBisqProxyError, Response.Status.INTERNAL_SERVER_ERROR);
    }
}

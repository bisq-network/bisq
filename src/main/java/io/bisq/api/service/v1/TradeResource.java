package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.TradeDetails;
import io.bisq.api.model.TradeList;
import io.bisq.api.service.ResourceHelper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static java.util.stream.Collectors.toList;

@Slf4j
@Api("trades")
@Produces(MediaType.APPLICATION_JSON)
public class TradeResource {

    private final BisqProxy bisqProxy;

    public TradeResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("List trades")
    @GET
    public TradeList find() {
        final TradeList tradeList = new TradeList();
        tradeList.trades = bisqProxy.getTradeList().stream().map(TradeDetails::new).collect(toList());
        tradeList.total = tradeList.trades.size();
        return tradeList;
    }

    @ApiOperation("Get trade details")
    @GET
    @Path("/{id}")
    public TradeDetails getById(@PathParam("id") String id) {
        return new TradeDetails(bisqProxy.getTrade(id));
    }

    @ApiOperation("Confirm payment has started")
    @POST
    @Path("/{id}/payment-started")
    public void paymentStarted(@PathParam("id") String id) {
        ResourceHelper.handleBisqProxyError(bisqProxy.paymentStarted(id), Response.Status.NOT_FOUND);
    }

    @ApiOperation("Confirm payment has been received")
    @POST
    @Path("/{id}/payment-received")
    public void paymentReceived(@PathParam("id") String id) {
        ResourceHelper.handleBisqProxyError(bisqProxy.paymentReceived(id), Response.Status.NOT_FOUND);
    }


    @ApiOperation("Move funds to Bisq wallet")
    @POST
    @Path("/{id}/move-funds-to-bisq-wallet")
    public void moveFundsToBisqWallet(@PathParam("id") String id) {
        if (!bisqProxy.moveFundsToBisqWallet(id)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
    }

}

package io.bisq.api.service.v1;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.bisq.api.BisqProxy;
import io.bisq.api.model.TradeList;
import io.bisq.api.service.ResourceHelper;
import io.bisq.core.trade.Trade;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

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
    @Path("/")
    public String find() {
        String result = "[]";
        TradeList tradeList = bisqProxy.getTradeList();
        if (tradeList == null || tradeList.trades == null || tradeList.trades.size() == 0) {
            // will use default result
        } else {
            try {
//                TODO build response using dedicated rest model
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

    @ApiOperation("Get trade details")
    @GET
    @Path("/{id}")
    public String getById(@PathParam("id") String id) {
        try {
            Optional<Trade> any = bisqProxy.getTrade(id);
            if (any.isPresent())
                return JsonFormat.printer().print(any.get().toProtoMessage());
            else
                throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (InvalidProtocolBufferException e) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
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

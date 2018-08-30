package bisq.httpapi.service.resources;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

import static bisq.httpapi.util.ResourceHelper.toValidationErrorResponse;
import static java.util.stream.Collectors.toList;



import bisq.httpapi.BisqProxy;
import bisq.httpapi.exceptions.NotFoundException;
import bisq.httpapi.model.TradeDetails;
import bisq.httpapi.model.TradeList;
import io.dropwizard.jersey.validation.ValidationErrorMessage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.validation.ValidationException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.hibernate.validator.constraints.NotEmpty;

@Slf4j
@Api(value = "trades", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class TradeEndpoint {

    private final BisqProxy bisqProxy;

    @Inject
    public TradeEndpoint(BisqProxy bisqProxy) {
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
    public void paymentStarted(@Suspended final AsyncResponse asyncResponse, @NotEmpty @PathParam("id") String id) {
        final CompletableFuture<Void> completableFuture = bisqProxy.paymentStarted(id);
        handlePaymentStatusChange(id, asyncResponse, completableFuture);
    }

    @ApiOperation("Confirm payment has been received")
    @POST
    @Path("/{id}/payment-received")
    public void paymentReceived(@Suspended final AsyncResponse asyncResponse, @NotEmpty @PathParam("id") String id) {
        final CompletableFuture<Void> completableFuture = bisqProxy.paymentReceived(id);
        handlePaymentStatusChange(id, asyncResponse, completableFuture);
    }

    @ApiOperation("Move funds to Bisq wallet")
    @POST
    @Path("/{id}/move-funds-to-bisq-wallet")
    public void moveFundsToBisqWallet(@PathParam("id") String id) {
        bisqProxy.moveFundsToBisqWallet(id);
    }

    private void handlePaymentStatusChange(String tradeId, AsyncResponse asyncResponse, CompletableFuture<Void> completableFuture) {
        completableFuture.thenApply(response -> asyncResponse.resume(Response.status(Response.Status.OK).build()))
                .exceptionally(e -> {
                    final Throwable cause = e.getCause();
                    final Response.ResponseBuilder responseBuilder;
                    if (cause instanceof ValidationException) {
                        responseBuilder = toValidationErrorResponse(cause, 422);
                    } else if (cause instanceof NotFoundException) {
                        responseBuilder = toValidationErrorResponse(cause, 404);
                    } else {
                        final String message = cause.getMessage();
                        responseBuilder = Response.status(500);
                        if (null != message)
                            responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
                        log.error("Unable to confirm payment started for trade: " + tradeId, cause);
                    }
                    return asyncResponse.resume(responseBuilder.build());
                });
    }

}

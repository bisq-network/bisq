package io.bisq.api.service.v1;

import com.google.common.collect.ImmutableList;
import io.bisq.api.*;
import io.bisq.api.NotFoundException;
import io.bisq.api.model.*;
import io.bisq.api.service.ResourceHelper;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.Trade;
import io.dropwizard.jersey.validation.ValidationErrorMessage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.util.Json;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;

//        TODO use more standard error handling than ResourceHelper.handleBisqProxyError
@Api("offers")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class OfferResource {

    private final BisqProxy bisqProxy;

    public OfferResource(BisqProxy bisqProxy) {

        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("Find offers")
    @GET
    public OfferList find() {
        final OfferList offerList = new OfferList();
        offerList.offers = bisqProxy.getOfferList().stream().map(OfferDetail::new).collect(toList());
        offerList.total = offerList.offers.size();
        return offerList;
    }

    @ApiOperation("Get offer details")
    @GET
    @Path("/{id}")
    public OfferDetail getOfferById(@NotEmpty @PathParam("id") String id) {
        return new OfferDetail(bisqProxy.getOffer(id));
    }

    @ApiOperation("Cancel offer")
    @DELETE
    @Path("/{id}")
    public void removeById(@PathParam("id") String id) {
        ResourceHelper.handleBisqProxyError(bisqProxy.offerCancel(id), Response.Status.NOT_FOUND);
    }

    @ApiOperation(value = "Create offer", response = OfferDetail.class)
    @POST
    public void create(@Suspended final AsyncResponse asyncResponse, @Valid OfferToCreate offer) {
        final CompletableFuture<Offer> completableFuture = bisqProxy.offerMake(
                offer.fundUsingBisqWallet,
                offer.offerId,
                offer.accountId,
                offer.direction,
                offer.amount,
                offer.minAmount,
                PriceType.PERCENTAGE.equals(offer.priceType),
                offer.percentageFromMarketPrice,
                offer.marketPair,
                offer.fixedPrice);
        completableFuture.thenApply(response -> asyncResponse.resume(new OfferDetail(response)))
                .exceptionally(e -> {
                    final Throwable cause = e.getCause();
                    final Response.ResponseBuilder responseBuilder;
                    if (cause instanceof ValidationException) {
                        final int status = 422;
                        responseBuilder = toResponse(cause, status);
                    } else if (cause instanceof IncompatiblePaymentAccountException) {
                        responseBuilder = toResponse(cause, 423);
                    } else if (cause instanceof NoAcceptedArbitratorException) {
                        responseBuilder = toResponse(cause, 424);
                    } else if (cause instanceof PaymentAccountNotFoundException) {
                        responseBuilder = toResponse(cause, 425);
                    } else if (cause instanceof InsufficientMoneyException) {
                        responseBuilder = toResponse(cause, 427);
                    } else {
                        final String message = cause.getMessage();
                        responseBuilder = Response.status(500);
                        if (null != message)
                            responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
                        log.error("Unable to create offer: " + Json.pretty(offer), cause);
                    }
                    return asyncResponse.resume(responseBuilder.build());
                });
    }

    private static Response.ResponseBuilder toResponse(Throwable cause, int status) {
        return Response.status(status).entity(new ValidationErrorMessage(ImmutableList.of(cause.getMessage())));
    }

    @ApiOperation(value = "Take offer", response = TradeDetails.class)
    @POST
    @Path("/{id}/take")
    public void takeOffer(@Suspended final AsyncResponse asyncResponse, @PathParam("id") String id, @Valid TakeOffer data) {
//        TODO how do we go about not blocking this REST thread?
        final CompletableFuture<Trade> completableFuture = bisqProxy.offerTake(id, data.paymentAccountId, data.amount, true);
        completableFuture.thenApply(trade -> asyncResponse.resume(new TradeDetails(trade)))
                .exceptionally(e -> {
                    final Throwable cause = e.getCause();
                    final Response.ResponseBuilder responseBuilder;
                    if (cause instanceof ValidationException) {
                        final int status = 422;
                        responseBuilder = toResponse(cause, status);
                    } else if (cause instanceof IncompatiblePaymentAccountException) {
                        responseBuilder = toResponse(cause, 423);
                    } else if (cause instanceof NoAcceptedArbitratorException) {
                        responseBuilder = toResponse(cause, 424);
                    } else if (cause instanceof PaymentAccountNotFoundException) {
                        responseBuilder = toResponse(cause, 425);
                    } else if (cause instanceof InsufficientMoneyException) {
                        responseBuilder = toResponse(cause, 427);
                    } else if (cause instanceof NotFoundException) {
                        responseBuilder = toResponse(cause, 404);
                    } else {
                        final String message = cause.getMessage();
                        responseBuilder = Response.status(500);
                        if (null != message)
                            responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
                        log.error("Unable to take offer: " + id + " " + Json.pretty(data), cause);
                    }
                    return asyncResponse.resume(responseBuilder.build());
                });
    }
}

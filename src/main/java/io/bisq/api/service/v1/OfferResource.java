package io.bisq.api.service.v1;

import com.google.common.collect.ImmutableList;
import io.bisq.api.*;
import io.bisq.api.NotFoundException;
import io.bisq.api.model.*;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Trade;
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

import static io.bisq.api.service.ResourceHelper.toValidationErrorResponse;
import static java.util.stream.Collectors.toList;

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
    public void cancelOffer(@Suspended final AsyncResponse asyncResponse, @PathParam("id") String id) {
        final CompletableFuture<Void> completableFuture = bisqProxy.offerCancel(id);
        completableFuture.thenApply(response -> asyncResponse.resume(Response.status(200).build()))
                .exceptionally(e -> {
                    final Throwable cause = e.getCause();
                    final Response.ResponseBuilder responseBuilder;
                    final String message = cause.getMessage();
                    if (cause instanceof NotFoundException) {
                        responseBuilder = toValidationErrorResponse(cause, 404);
                    } else {
                        responseBuilder = Response.status(500);
                        if (null != message)
                            responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
                        log.error("Unable to remove offer: " + id, cause);
                    }
                    return asyncResponse.resume(responseBuilder.build());
                });

    }

    @ApiOperation(value = "Create offer", response = OfferDetail.class)
    @POST
    public void createOffer(@Suspended final AsyncResponse asyncResponse, @Valid OfferToCreate offer) {
        final OfferPayload.Direction direction = OfferPayload.Direction.valueOf(offer.direction);
        final PriceType priceType = PriceType.valueOf(offer.priceType);
        final Double marketPriceMargin = null == offer.percentageFromMarketPrice ? null : offer.percentageFromMarketPrice.doubleValue();
        final CompletableFuture<Offer> completableFuture = bisqProxy.offerMake(
                offer.fundUsingBisqWallet,
                offer.offerId,
                offer.accountId,
                direction,
                offer.amount,
                offer.minAmount,
                PriceType.PERCENTAGE.equals(priceType),
                marketPriceMargin,
                offer.marketPair,
                offer.fixedPrice, offer.buyerSecurityDeposit);
        completableFuture.thenApply(response -> asyncResponse.resume(new OfferDetail(response)))
                .exceptionally(e -> {
                    final Throwable cause = e.getCause();
                    final Response.ResponseBuilder responseBuilder;
                    if (cause instanceof ValidationException) {
                        final int status = 422;
                        responseBuilder = toValidationErrorResponse(cause, status);
                    } else if (cause instanceof IncompatiblePaymentAccountException) {
                        responseBuilder = toValidationErrorResponse(cause, 423);
                    } else if (cause instanceof NoAcceptedArbitratorException) {
                        responseBuilder = toValidationErrorResponse(cause, 424);
                    } else if (cause instanceof PaymentAccountNotFoundException) {
                        responseBuilder = toValidationErrorResponse(cause, 425);
                    } else if (cause instanceof InsufficientMoneyException) {
                        responseBuilder = toValidationErrorResponse(cause, 427);
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
                        responseBuilder = toValidationErrorResponse(cause, status);
                    } else if (cause instanceof IncompatiblePaymentAccountException) {
                        responseBuilder = toValidationErrorResponse(cause, 423);
                    } else if (cause instanceof NoAcceptedArbitratorException) {
                        responseBuilder = toValidationErrorResponse(cause, 424);
                    } else if (cause instanceof PaymentAccountNotFoundException) {
                        responseBuilder = toValidationErrorResponse(cause, 425);
                    } else if (cause instanceof InsufficientMoneyException) {
                        responseBuilder = toValidationErrorResponse(cause, 427);
                    } else if (cause instanceof NotFoundException) {
                        responseBuilder = toValidationErrorResponse(cause, 404);
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

package bisq.httpapi.service.endpoint;

import bisq.core.offer.Offer;
import bisq.core.trade.Trade;

import bisq.common.UserThread;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

import static bisq.httpapi.util.ResourceHelper.toValidationErrorResponse;



import bisq.httpapi.exceptions.AmountTooHighException;
import bisq.httpapi.exceptions.IncompatiblePaymentAccountException;
import bisq.httpapi.exceptions.InsufficientMoneyException;
import bisq.httpapi.exceptions.NoAcceptedArbitratorException;
import bisq.httpapi.exceptions.NotFoundException;
import bisq.httpapi.exceptions.PaymentAccountNotFoundException;
import bisq.httpapi.facade.OfferFacade;
import bisq.httpapi.model.InputDataForOffer;
import bisq.httpapi.model.OfferDetail;
import bisq.httpapi.model.OfferList;
import bisq.httpapi.model.TakeOffer;
import bisq.httpapi.model.TradeDetails;
import bisq.httpapi.util.ResourceHelper;
import io.dropwizard.jersey.validation.ValidationErrorMessage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import io.swagger.util.Json;
import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.ws.rs.DELETE;
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

@Api(value = "offers", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class OfferEndpoint {

    private final OfferFacade offerFacade;


    @Inject
    public OfferEndpoint(OfferFacade offerFacade) {
        this.offerFacade = offerFacade;
    }


    @ApiOperation(value = "Find offers", response = OfferList.class)
    @GET
    public void find(@Suspended final AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                List<OfferDetail> offers = offerFacade.getAllOffers();
                asyncResponse.resume(new OfferList(offers));
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Get offer details", response = OfferDetail.class)
    @GET
    @Path("/{id}")
    public void getOfferById(@Suspended final AsyncResponse asyncResponse, @NotEmpty @PathParam("id") String id) {
        UserThread.execute(() -> {
            try {
                Offer offer = offerFacade.findOffer(id);
                asyncResponse.resume(new OfferDetail(offer));
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation("Cancel offer")
    @DELETE
    @Path("/{id}")
    public void cancelOffer(@Suspended AsyncResponse asyncResponse, @PathParam("id") String id) {
        CompletableFuture<Void> completableFuture = offerFacade.cancelOffer(id);
        completableFuture.thenApply(response -> asyncResponse.resume(Response.status(200).build()))
                .exceptionally(throwable -> ResourceHelper.handleException(asyncResponse, throwable));
    }

    @ApiOperation(value = "Create offer", response = OfferDetail.class)
    @POST
    public void createOffer(@Suspended final AsyncResponse asyncResponse, @Valid InputDataForOffer input) {
        CompletableFuture<Offer> completableFuture = offerFacade.createOffer(input);
        completableFuture.thenApply(response -> asyncResponse.resume(new OfferDetail(response)))
                .exceptionally(e -> {
                    final Throwable cause = e.getCause();
                    final Response.ResponseBuilder responseBuilder;
                    if (cause instanceof ValidationException) {
                        responseBuilder = toValidationErrorResponse(cause, 422);
                    } else if (cause instanceof IncompatiblePaymentAccountException) {
                        responseBuilder = toValidationErrorResponse(cause, 423);
                    } else if (cause instanceof NoAcceptedArbitratorException) {
                        responseBuilder = toValidationErrorResponse(cause, 424);
                    } else if (cause instanceof PaymentAccountNotFoundException) {
                        responseBuilder = toValidationErrorResponse(cause, 425);
                    } else if (cause instanceof AmountTooHighException) {
                        responseBuilder = toValidationErrorResponse(cause, 426);
                    } else if (cause instanceof InsufficientMoneyException) {
                        responseBuilder = toValidationErrorResponse(cause, 427);
                    } else {
                        String message = cause.getMessage();
                        responseBuilder = Response.status(500);
                        if (null != message)
                            responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
                        log.error("Unable to create offer: " + Json.pretty(input), cause);
                    }
                    return asyncResponse.resume(responseBuilder.build());
                });
    }

    @ApiOperation(value = "Take offer", response = TradeDetails.class)
    @POST
    @Path("/{id}/take")
    public void takeOffer(@Suspended final AsyncResponse asyncResponse, @PathParam("id") String id, @Valid TakeOffer data) {
        UserThread.execute(() -> {
            try {
                final CompletableFuture<Trade> completableFuture = offerFacade.offerTake(id, data.paymentAccountId, data.amount, true, data.maxFundsForTrade);
                completableFuture.thenApply(trade -> asyncResponse.resume(new TradeDetails(trade)))
                        .exceptionally(e -> {
                            final Throwable cause = e.getCause();
                            final Response.ResponseBuilder responseBuilder;
                            if (cause instanceof ValidationException) {
                                final int status = 422;
                                responseBuilder = toValidationErrorResponse(cause, status);
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
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }
}

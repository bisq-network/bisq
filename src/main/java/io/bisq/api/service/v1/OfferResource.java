package io.bisq.api.service.v1;

import com.google.common.collect.ImmutableList;
import io.bisq.api.*;
import io.bisq.api.model.OfferDetail;
import io.bisq.api.model.OfferToCreate;
import io.bisq.api.model.PriceType;
import io.bisq.api.model.TakeOffer;
import io.bisq.api.service.ResourceHelper;
import io.bisq.common.util.Tuple2;
import io.dropwizard.jersey.validation.ValidationErrorMessage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Transaction;

import javax.validation.ValidationException;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

//        TODO use more standard error handling
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
    @Path("/")
    public Collection<OfferDetail> find() {
        return bisqProxy.getOfferList();
    }

    @ApiOperation("Get offer details")
    @GET
    @Path("/{id}")
    public OfferDetail getById(@PathParam("id") String id) throws Exception {
        Tuple2<Optional<OfferDetail>, Optional<BisqProxyError>> result = bisqProxy.getOfferDetail(id);
        if (!result.first.isPresent()) {
            ResourceHelper.handleBisqProxyError(result.second);
        }

        return result.first.get();
    }

    @ApiOperation("Cancel offer")
    @DELETE
    @Path("/{id}")
    public void removeById(@PathParam("id") String id) {
        ResourceHelper.handleBisqProxyError(bisqProxy.offerCancel(id), Response.Status.NOT_FOUND);
    }

    @ApiOperation("Create offer")
    @POST
    public void create(@Suspended final AsyncResponse asyncResponse, OfferToCreate offer) {
//        TODO should return created offer
        final CompletableFuture<Transaction> completableFuture = bisqProxy.offerMake(
                offer.accountId,
                offer.direction,
                offer.amount,
                offer.minAmount,
                PriceType.PERCENTAGE.equals(offer.priceType),
                offer.percentage_from_market_price,
                offer.marketPair,
                offer.fixedPrice);
        completableFuture.thenApply(asyncResponse::resume)
                .exceptionally(e -> {
                    final Throwable cause = e.getCause();
                    Response.ResponseBuilder responseBuilder;
                    if (cause instanceof ValidationException) {
                        responseBuilder = Response.status(422).entity(new ValidationErrorMessage(ImmutableList.of(cause.getMessage())));
                    } else if (cause instanceof IncompatiblePaymentAccountException) {
                        responseBuilder = Response.status(423).entity(new ValidationErrorMessage(ImmutableList.of(cause.getMessage())));
                    } else if (cause instanceof NoAcceptedArbitratorException) {
                        responseBuilder = Response.status(424).entity(new ValidationErrorMessage(ImmutableList.of(cause.getMessage())));
                    } else if (cause instanceof NoPaymentAccountException) {
                        responseBuilder = Response.status(425).entity(new ValidationErrorMessage(ImmutableList.of(cause.getMessage())));
                    } else {
                        responseBuilder = Response.status(500).entity(new ValidationErrorMessage(ImmutableList.of(cause.getMessage())));
                        log.error("Unable to create offer.", cause);
                    }
                    return asyncResponse.resume(responseBuilder.build());
                });
    }

    @ApiOperation("Take offer")
    @POST
    @Path("/{id}/take")
    public boolean takeOffer(@PathParam("id") String id, TakeOffer data) {
//        TODO this definitely should return something different then boolean, at least wrapped with jsob
        return ResourceHelper.handleBisqProxyError(bisqProxy.offerTake(id, data.paymentAccountId, data.amount, true));
    }
}

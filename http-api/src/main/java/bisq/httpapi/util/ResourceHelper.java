package bisq.httpapi.util;


import com.google.common.collect.ImmutableList;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;



import bisq.httpapi.exceptions.NotFoundException;
import io.dropwizard.jersey.validation.ValidationErrorMessage;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;

@Slf4j
public final class ResourceHelper {

    public static Response.ResponseBuilder toValidationErrorResponse(Throwable cause, int status) {
        final String message = cause.getMessage();
        final ImmutableList<String> list = null == message ? ImmutableList.of() : ImmutableList.of(message);
        return Response.status(status).entity(new ValidationErrorMessage(list));
    }

    public static boolean handleException(AsyncResponse asyncResponse, Throwable throwable) {
        final Throwable cause = throwable.getCause();
        final Response.ResponseBuilder responseBuilder;
        final String message = cause.getMessage();
        if (cause instanceof NotFoundException) {
            responseBuilder = toValidationErrorResponse(cause, 404);
        } else {
            responseBuilder = Response.status(500);
            if (null != message)
                responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
            log.error("Unable to remove offer: throwable={}" + throwable);
        }
        return asyncResponse.resume(responseBuilder.build());
    }

    public static <T> CompletableFuture<T> completeExceptionally(CompletableFuture<T> futureResult, Throwable throwable) {
        futureResult.completeExceptionally(throwable);
        return futureResult;
    }
}

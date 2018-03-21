package io.bisq.api.service;


import com.google.common.collect.ImmutableList;
import io.bisq.api.BisqProxyError;
import io.dropwizard.jersey.validation.ValidationErrorMessage;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.Optional;

public final class ResourceHelper {

    private ResourceHelper() {
    }

    public static boolean handleBisqProxyError(Optional<BisqProxyError> optionalBisqProxyError, Response.Status status) {

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

    public static boolean handleBisqProxyError(Optional<BisqProxyError> optionalBisqProxyError) {
        return handleBisqProxyError(optionalBisqProxyError, Response.Status.INTERNAL_SERVER_ERROR);
    }

    public static Response.ResponseBuilder toValidationErrorResponse(Throwable cause, int status) {
        return Response.status(status).entity(new ValidationErrorMessage(ImmutableList.of(cause.getMessage())));
    }
}

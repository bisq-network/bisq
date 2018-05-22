package network.bisq.api.service;


import com.google.common.collect.ImmutableList;
import io.dropwizard.jersey.validation.ValidationErrorMessage;

import javax.ws.rs.core.Response;

public final class ResourceHelper {

    private ResourceHelper() {
    }

    public static Response.ResponseBuilder toValidationErrorResponse(Throwable cause, int status) {
        final String message = cause.getMessage();
        final ImmutableList<String> list = null == message ? ImmutableList.of() : ImmutableList.of(message);
        return Response.status(status).entity(new ValidationErrorMessage(list));
    }
}

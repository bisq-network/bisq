package io.bisq.api.service;


import com.google.common.collect.ImmutableList;
import io.dropwizard.jersey.validation.ValidationErrorMessage;

import javax.ws.rs.core.Response;

public final class ResourceHelper {

    private ResourceHelper() {
    }

    public static Response.ResponseBuilder toValidationErrorResponse(Throwable cause, int status) {
        return Response.status(status).entity(new ValidationErrorMessage(ImmutableList.of(cause.getMessage())));
    }
}

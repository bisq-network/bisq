package io.bisq.api.service;

import com.google.common.collect.ImmutableList;
import io.bisq.api.NotFoundException;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.jersey.validation.ValidationErrorMessage;

import javax.validation.ValidationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public final class ExceptionMappers {

    private ExceptionMappers() {
    }

    public static void register(JerseyEnvironment environment) {
        environment.register(new ExceptionMappers.NotFoundExceptionMapper());
        environment.register(new ExceptionMappers.ValidationExceptionMapper());
    }

    public static class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
        @Override
        public Response toResponse(NotFoundException exception) {
            return Response.status(404).entity(exception.getMessage()).type(MediaType.TEXT_PLAIN_TYPE).build();
        }
    }

    public static class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {
        @Override
        public Response toResponse(ValidationException exception) {
            return Response.status(422).entity(new ValidationErrorMessage(ImmutableList.of(exception.getMessage()))).build();
        }
    }
}

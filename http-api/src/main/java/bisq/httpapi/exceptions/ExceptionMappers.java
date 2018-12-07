package bisq.httpapi.exceptions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;

import com.google.common.collect.ImmutableList;



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
        environment.register(new ExceptionMappers.ExperimentalFeatureExceptionMapper());
        environment.register(new ExceptionMappers.InvalidTypeIdExceptionMapper());
        environment.register(new ExceptionMappers.NotFoundExceptionMapper());
        environment.register(new ExceptionMappers.ValidationExceptionMapper());
        environment.register(new ExceptionMappers.WalletNotReadyExceptionMapper());
        environment.register(new ExceptionMappers.UnauthorizedExceptionMapper());
    }

    public static class InvalidTypeIdExceptionMapper implements ExceptionMapper<InvalidTypeIdException> {
        @Override
        public Response toResponse(InvalidTypeIdException exception) {
            final Class<?> rawClass = exception.getBaseType().getRawClass();
            final StringBuilder builder = new StringBuilder("Unable to recognize sub type of ")
                    .append(rawClass.getSimpleName())
                    .append(". Value '")
                    .append(exception.getTypeId())
                    .append("' is invalid.");

            final JsonSubTypes annotation = rawClass.getAnnotation(JsonSubTypes.class);
            if (null != annotation && 0 < annotation.value().length) {
                builder.append(" Allowed values are: ");
                final String separator = ", ";
                for (JsonSubTypes.Type subType : annotation.value())
                    builder.append(subType.name()).append(separator);
                builder.delete(builder.length() - separator.length(), builder.length());
            }

            return Response.status(422).entity(new ValidationErrorMessage(ImmutableList.of(builder.toString()))).build();
        }
    }

    public static class ExperimentalFeatureExceptionMapper implements ExceptionMapper<ExperimentalFeatureException> {
        @Override
        public Response toResponse(ExperimentalFeatureException exception) {
            return ExceptionMappers.toResponse(exception, Response.Status.NOT_IMPLEMENTED);
        }
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

    public static class WalletNotReadyExceptionMapper implements ExceptionMapper<WalletNotReadyException> {
        @Override
        public Response toResponse(WalletNotReadyException exception) {
            return ExceptionMappers.toResponse(exception, Response.Status.SERVICE_UNAVAILABLE);
        }
    }

    public static class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
        @Override
        public Response toResponse(UnauthorizedException exception) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    private static Response toResponse(Throwable throwable, Response.Status status) {
        return Response.status(status).entity(new ValidationErrorMessage(ImmutableList.of(throwable.getMessage()))).type(MediaType.APPLICATION_JSON).build();
    }
}

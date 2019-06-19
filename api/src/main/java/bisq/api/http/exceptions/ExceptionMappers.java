package bisq.api.http.exceptions;

import bisq.api.http.service.ValidationErrorMessage;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;

import com.google.common.collect.ImmutableList;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;



import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import javax.validation.ValidationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import org.eclipse.jetty.io.EofException;
import org.glassfish.jersey.server.ResourceConfig;

@Slf4j
public final class ExceptionMappers {

    private ExceptionMappers() {
    }

    public static void register(ResourceConfig environment) {
        environment.register(new ExceptionMappers.EofExceptionMapper(), 1);
        environment.register(new ExceptionMappers.JsonParseExceptionMapper(), 1);
        environment.register(new ExceptionMappers.BisqValidationExceptionMapper());
        environment.register(new ExceptionMappers.ExperimentalFeatureExceptionMapper());
        environment.register(new ExceptionMappers.InvalidTypeIdExceptionMapper());
        environment.register(new ExceptionMappers.NotFoundExceptionMapper());
        environment.register(new ExceptionMappers.ValidationExceptionMapper());
        environment.register(new ExceptionMappers.UnauthorizedExceptionMapper());
    }

    private static Response toResponse(Throwable throwable, Response.Status status) {
        Response.ResponseBuilder responseBuilder = Response.status(status);
        String message = throwable.getMessage();
        if (message != null) {
            responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
        }
        return responseBuilder.type(MediaType.APPLICATION_JSON).build();
    }

    public static class BisqValidationExceptionMapper implements ExceptionMapper<bisq.core.exceptions.ValidationException> {
        @Override
        public Response toResponse(bisq.core.exceptions.ValidationException exception) {
            Response.ResponseBuilder responseBuilder = Response.status(422);
            String message = exception.getMessage();
            responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
            return responseBuilder.build();
        }
    }

    public static class EofExceptionMapper implements ExceptionMapper<EofException> {
        @Override
        public Response toResponse(EofException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    public static class JsonParseExceptionMapper implements ExceptionMapper<JsonParseException> {
        @Override
        public Response toResponse(JsonParseException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    public static class InvalidTypeIdExceptionMapper implements ExceptionMapper<InvalidTypeIdException> {
        @Override
        public Response toResponse(InvalidTypeIdException exception) {
            Class<?> rawClass = exception.getBaseType().getRawClass();
            StringBuilder builder = new StringBuilder("Unable to recognize sub type of ")
                    .append(rawClass.getSimpleName())
                    .append(". Value '")
                    .append(exception.getTypeId())
                    .append("' is invalid.");

            JsonSubTypes annotation = rawClass.getAnnotation(JsonSubTypes.class);
            if (annotation != null && annotation.value().length > 0) {
                builder.append(" Allowed values are: ");
                String separator = ", ";
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
            Response.ResponseBuilder responseBuilder = Response.status(422);
            String message = exception.getMessage();
            if (exception instanceof ConstraintViolationException) {
                List<String> messages = ((ConstraintViolationException) exception).getConstraintViolations().stream().map(constraintViolation -> {
                    StringBuilder stringBuilder = new StringBuilder();
                    Path propertyPath = constraintViolation.getPropertyPath();
                    if (propertyPath != null) {
                        Iterator<Path.Node> pathIterator = constraintViolation.getPropertyPath().iterator();
                        String node = null;
                        while (pathIterator.hasNext())
                            node = pathIterator.next().getName();
                        if (node != null)
                            stringBuilder.append(node).append(" ");
                    }
                    return stringBuilder.append(constraintViolation.getMessage()).toString();
                }).collect(Collectors.toList());
                responseBuilder.entity(new ValidationErrorMessage(ImmutableList.copyOf(messages)));
            } else if (message != null) {
                responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
            }
            return responseBuilder.build();
        }
    }

    public static class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
        @Override
        public Response toResponse(UnauthorizedException exception) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }
}

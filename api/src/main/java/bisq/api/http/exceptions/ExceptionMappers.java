package bisq.api.http.exceptions;

import com.fasterxml.jackson.core.JsonParseException;

import lombok.extern.slf4j.Slf4j;



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
        environment.register(new ExceptionMappers.UnauthorizedExceptionMapper());
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

    public static class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
        @Override
        public Response toResponse(UnauthorizedException exception) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }
}

package bisq.core.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Metadata.Key;
import static io.grpc.Status.UNAUTHENTICATED;
import static java.lang.String.format;

/**
 * Authorizes rpc server calls by comparing the value of the caller's
 * {@value AUTH_HEADER_KEY} header to an expected value set at server startup time.
 *
 * @see bisq.common.config.Config#apiPassword
 */
@Slf4j
public class AuthorizationInterceptor implements ServerInterceptor {

    public static final String AUTH_HEADER_KEY = "authorization";

    private final String expectedAuthHeaderValue;

    public AuthorizationInterceptor(String expectedAuthHeaderValue) {
        this.expectedAuthHeaderValue = expectedAuthHeaderValue;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> serverCallHandler) {
        var actualAuthHeaderValue = headers.get(Key.of(AUTH_HEADER_KEY, ASCII_STRING_MARSHALLER));

        if (actualAuthHeaderValue == null)
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription(
                    format("missing '%s' rpc header value", AUTH_HEADER_KEY)));

        if (!actualAuthHeaderValue.equals(expectedAuthHeaderValue))
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription(
                    format("incorrect '%s' rpc header value", AUTH_HEADER_KEY)));

        return serverCallHandler.startCall(serverCall, headers);
    }
}

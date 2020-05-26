package bisq.core.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.StatusRuntimeException;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Metadata.Key;
import static io.grpc.Status.UNAUTHENTICATED;
import static java.lang.String.format;

/**
 * Authorizes rpc server calls by comparing the value of the caller's
 * {@value PASSWORD_KEY} header to an expected value set at server startup time.
 *
 * @see bisq.common.config.Config#apiPassword
 */
class PasswordAuthInterceptor implements ServerInterceptor {

    static final Context.Key<Boolean> HTTP1_REQUEST_CTX_KEY = Context.key("HTTP1_REQUEST_CTX_KEY");

    private static final Key<String> HTTP_GATEWAY_AUTH_KEY = Key.of("grpcgateway-authorization", ASCII_STRING_MARSHALLER);

    private static final String PASSWORD_KEY = "password";

    private final String expectedPasswordValue;

    public PasswordAuthInterceptor(String expectedPasswordValue) {
        this.expectedPasswordValue = expectedPasswordValue;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> serverCallHandler) {
        var isGatewayRequest = headers.containsKey(HTTP_GATEWAY_AUTH_KEY);
        var actualPasswordValue = isGatewayRequest
                ? headers.get(HTTP_GATEWAY_AUTH_KEY)
                : headers.get(Key.of(PASSWORD_KEY, ASCII_STRING_MARSHALLER));

        if (actualPasswordValue == null)
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription(
                    format("missing '%s' rpc header value", PASSWORD_KEY)));

        if (!actualPasswordValue.equals(expectedPasswordValue))
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription(
                    format("incorrect '%s' rpc header value", PASSWORD_KEY)));

        Context newCtx = isGatewayRequest
                ? Context.current().withValue(HTTP1_REQUEST_CTX_KEY, true)
                : Context.current().withValue(HTTP1_REQUEST_CTX_KEY, false);
        return Contexts.interceptCall(newCtx, serverCall, headers, serverCallHandler);
    }
}

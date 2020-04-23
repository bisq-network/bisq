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

/**
 * Simple authentication interceptor to validate a cleartext token in username:password format.
 */
@Slf4j
public class TokenAuthInterceptor implements ServerInterceptor {

    private final String apiToken;

    public TokenAuthInterceptor(String apiToken) {
        this.apiToken = apiToken;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata,
                                                                 ServerCallHandler<ReqT, RespT> serverCallHandler) {
        authenticate(metadata.get(Key.of("bisq-api-token", ASCII_STRING_MARSHALLER)));
        return serverCallHandler.startCall(serverCall, metadata);
    }

    private void authenticate(String authToken) {
        if (authToken == null)
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription("API token is missing"));

        if (!authToken.equals(apiToken))
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription("Invalid API token"));
    }
}

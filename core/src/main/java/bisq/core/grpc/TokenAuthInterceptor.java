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

    private final String rpcUser;
    private final String rpcPassword;

    public TokenAuthInterceptor(String rpcUser, String rpcPassword) {
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata,
                                                                 ServerCallHandler<ReqT, RespT> serverCallHandler) {
        authenticate(metadata.get(Key.of("bisq-api-token", ASCII_STRING_MARSHALLER)));
        return serverCallHandler.startCall(serverCall, metadata);
    }

    private void authenticate(String authToken) {
        if (authToken == null)
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription("Authentication token is missing"));

        if (!isValidToken(authToken))
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription("Invalid username or password"));

        log.info("Authenticated user {} with token {}", rpcUser, authToken);
    }

    private boolean isValidToken(String token) {
        String[] pair = token.split(":");
        return pair[0].equals(rpcUser) && pair[1].equals(rpcPassword);
    }
}

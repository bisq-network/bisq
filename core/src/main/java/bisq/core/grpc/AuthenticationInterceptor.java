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
public class AuthenticationInterceptor implements ServerInterceptor {

    private final String rpcUser;
    private final String rpcPassword;

    public AuthenticationInterceptor(String rpcUser, String rpcPassword) {
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler) {
        authenticate(metadata);
        return serverCallHandler.startCall(serverCall, metadata);
    }

    private void authenticate(Metadata metadata) {
        final String authToken = metadata.get(Key.of("bisqd-creds", ASCII_STRING_MARSHALLER));
        if (authToken == null) {
            throw new StatusRuntimeException(UNAUTHENTICATED.withDescription("Authentication token is missing"));
        } else {
            try {
                if (isValidToken(authToken)) {
                    log.info("Authenticated user {} with token {}", rpcUser, authToken);
                } else {
                    throw new StatusRuntimeException(UNAUTHENTICATED.withDescription("Invalid username or password"));
                }
            } catch (Exception e) {
                throw new StatusRuntimeException(UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e));
            }
        }
    }

    private boolean isValidToken(String token) {
        String[] pair = token.split(":");
        return pair[0].equals(rpcUser) && pair[1].equals(rpcPassword);
    }
}

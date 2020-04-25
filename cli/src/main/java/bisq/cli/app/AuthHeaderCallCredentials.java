package bisq.cli.app;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;

import java.util.concurrent.Executor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.UNAUTHENTICATED;

/**
 * Sets the {@value AUTH_HEADER_KEY} rpc call header to a given value.
 */
public class AuthHeaderCallCredentials extends CallCredentials {

    public static final String AUTH_HEADER_KEY = "authorization";

    private final String authHeaderValue;

    public AuthHeaderCallCredentials(String authHeaderValue) {
        this.authHeaderValue = authHeaderValue;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier metadataApplier) {
        appExecutor.execute(() -> {
            try {
                var headers = new Metadata();
                var authorizationKey = Key.of(AUTH_HEADER_KEY, ASCII_STRING_MARSHALLER);
                headers.put(authorizationKey, authHeaderValue);
                metadataApplier.apply(headers);
            } catch (Throwable ex) {
                metadataApplier.fail(UNAUTHENTICATED.withCause(ex));
            }
        });
    }

    @Override
    public void thisUsesUnstableApi() {
    }
}

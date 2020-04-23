package bisq.cli.app;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;

import java.util.concurrent.Executor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.UNAUTHENTICATED;

/**
 * Simple credentials implementation for sending cleartext username:password token via rpc call headers.
 */
public class BisqCallCredentials extends CallCredentials {

    private final String apiToken;

    public BisqCallCredentials(String apiToken) {
        this.apiToken = apiToken;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier metadataApplier) {
        appExecutor.execute(() -> {
            try {
                Metadata headers = new Metadata();
                Key<String> apiTokenKey = Key.of("bisq-api-token", ASCII_STRING_MARSHALLER);
                headers.put(apiTokenKey, apiToken);
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

package bisq.cli.app;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;

import java.util.Map;
import java.util.concurrent.Executor;

import static bisq.cli.app.CliConfig.RPC_PASSWORD;
import static bisq.cli.app.CliConfig.RPC_USER;
import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.UNAUTHENTICATED;

/**
 * Simple credentials implementation for sending cleartext username:password token via rpc call headers.
 */
public class BisqCallCredentials extends CallCredentials {

    private final Map<String, String> credentials;

    public BisqCallCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier metadataApplier) {
        appExecutor.execute(() -> {
            try {
                Metadata headers = new Metadata();
                Key<String> creds = Key.of("bisqd-creds", ASCII_STRING_MARSHALLER);
                headers.put(creds, encodeCredentials());
                metadataApplier.apply(headers);
            } catch (Throwable ex) {
                metadataApplier.fail(UNAUTHENTICATED.withCause(ex));
            }
        });
    }

    private String encodeCredentials() {
        if (!credentials.containsKey(RPC_USER) || !credentials.containsKey(RPC_PASSWORD))
            throw new ConfigException("Cannot call rpc service without username:password credentials");

        return credentials.get(RPC_USER) + ":" + credentials.get(RPC_PASSWORD);
    }

    @Override
    public void thisUsesUnstableApi() {
    }
}

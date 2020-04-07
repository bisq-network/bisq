package bisq.core.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.apache.commons.codec.binary.Hex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import static java.nio.charset.StandardCharsets.UTF_8;



import com.github.nitram509.jmacaroons.Macaroon;
import com.github.nitram509.jmacaroons.MacaroonsBuilder;


@Slf4j
class AuthenticationInterceptor implements ServerInterceptor {

    private final Macaroon macaroon;

    public AuthenticationInterceptor(File appDataDir) {
        this.macaroon = loadMacaroon(appDataDir);
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
        final String authToken = metadata.get(Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER));
        if (authToken == null) {
            throw new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Authentication token is missing"));
        } else {
            try {
                Macaroon macaroon = MacaroonsBuilder.deserialize(new String(Hex.decodeHex(authToken), UTF_8).trim());
                if (this.macaroon.signature.equals(macaroon.signature)) {
                    log.info("Successfully authenticated macaroon with identifier {}", macaroon.identifier);
                } else {
                    throw new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Authentication token is invalid"));
                }
                /*
                TODO Compare client's macaroon.sig with server's macaroon sig (above), OR cache a secret and use verifier (below)?
                MacaroonsVerifier verifier = new MacaroonsVerifier(macaroon);
                if (verifier.isValid("0123456789")) {
                    log.info("Successfully authenticated macaroon with identifier {}", macaroon.identifier);
                } else {
                    throw new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Authentication token is invalid"));
                }
                */
            } catch (Exception e) {
                throw new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e));
            }
        }
    }

    private Macaroon loadMacaroon(File appDataDir) {
        Predicate<Path> isValid = (p) -> p.toFile().exists() && p.toFile().length() > 0;
        try {
            Path macaroonPath = Paths.get(appDataDir.getAbsolutePath(), "bisqd.macaroon");
            if (isValid.test(macaroonPath)) {
                String base64Macaroon = Files.readAllLines(macaroonPath, UTF_8).get(0);
                return MacaroonsBuilder.deserialize(base64Macaroon);
            } else {
                throw new RuntimeException("gRPC server macaroon was not found in " + appDataDir.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("gRPC server macaroon could not be decoded");
        }
    }
}

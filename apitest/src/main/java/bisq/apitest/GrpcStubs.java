/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.apitest;

import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.OffersGrpc;
import bisq.proto.grpc.PaymentAccountsGrpc;
import bisq.proto.grpc.WalletsGrpc;

import io.grpc.CallCredentials;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;

import java.util.concurrent.Executor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.UNAUTHENTICATED;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.config.BisqAppConfig;

public class GrpcStubs {

    public final CallCredentials credentials;
    public final String host;
    public final int port;

    public GetVersionGrpc.GetVersionBlockingStub versionService;
    public OffersGrpc.OffersBlockingStub offersService;
    public PaymentAccountsGrpc.PaymentAccountsBlockingStub paymentAccountsService;
    public WalletsGrpc.WalletsBlockingStub walletsService;

    public GrpcStubs(BisqAppConfig bisqAppConfig, ApiTestConfig config) {
        this.credentials = new PasswordCallCredentials(config.apiPassword);
        this.host = "localhost";
        this.port = bisqAppConfig.apiPort;
    }

    GrpcStubs init() {
        var channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                channel.shutdown().awaitTermination(1, SECONDS);
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
        }));

        this.versionService = GetVersionGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.offersService = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.paymentAccountsService = PaymentAccountsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.walletsService = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);

        return this;
    }

    static class PasswordCallCredentials extends CallCredentials {

        public static final String PASSWORD_KEY = "password";
        private final String passwordValue;

        public PasswordCallCredentials(String passwordValue) {
            if (passwordValue == null)
                throw new IllegalArgumentException(format("'%s' value must not be null", PASSWORD_KEY));
            this.passwordValue = passwordValue;
        }

        @Override
        public void applyRequestMetadata(RequestInfo requestInfo,
                                         Executor appExecutor,
                                         MetadataApplier metadataApplier) {
            appExecutor.execute(() -> {
                try {
                    var headers = new Metadata();
                    var passwordKey = Metadata.Key.of(PASSWORD_KEY, ASCII_STRING_MARSHALLER);
                    headers.put(passwordKey, passwordValue);
                    metadataApplier.apply(headers);
                } catch (Throwable ex) {
                    metadataApplier.fail(UNAUTHENTICATED.withCause(ex));
                }
            });
        }

        @Override
        public void thisUsesUnstableApi() {
            // An experimental api.  A noop but never called; tries to make it clearer to
            // implementors that they may break in the future.
        }
    }
}

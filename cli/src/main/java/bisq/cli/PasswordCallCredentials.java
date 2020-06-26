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

package bisq.cli;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;

import java.util.concurrent.Executor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.UNAUTHENTICATED;
import static java.lang.String.format;

/**
 * Sets the {@value PASSWORD_KEY} rpc call header to a given value.
 */
class PasswordCallCredentials extends CallCredentials {

    public static final String PASSWORD_KEY = "password";

    private final String passwordValue;

    public PasswordCallCredentials(String passwordValue) {
        if (passwordValue == null)
            throw new IllegalArgumentException(format("'%s' value must not be null", PASSWORD_KEY));
        this.passwordValue = passwordValue;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier metadataApplier) {
        appExecutor.execute(() -> {
            try {
                var headers = new Metadata();
                var passwordKey = Key.of(PASSWORD_KEY, ASCII_STRING_MARSHALLER);
                headers.put(passwordKey, passwordValue);
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

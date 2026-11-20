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

package bisq.daemon.grpc.interceptor;

import bisq.common.config.Config;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class PasswordAuthInterceptorTest {
    private static final Metadata.Key<String> PASSWORD_KEY = Metadata.Key.of("password", ASCII_STRING_MARSHALLER);

    @Test
    public void rejectsMissingOrBlankApiPassword() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordAuthInterceptor(new Config()));
        assertThrows(IllegalArgumentException.class,
                () -> new PasswordAuthInterceptor(new Config("--apiPassword= ")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void startsOnlyCallsWithTheConfiguredPassword() {
        PasswordAuthInterceptor interceptor = new PasswordAuthInterceptor(new Config("--apiPassword=xyz"));
        ServerCall<Object, Object> serverCall = mock(ServerCall.class);
        ServerCallHandler<Object, Object> serverCallHandler = mock(ServerCallHandler.class);

        for (Metadata headers : new Metadata[]{headers(null), headers(""), headers("xy")}) {
            StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
                    () -> interceptor.interceptCall(serverCall, headers, serverCallHandler));
            assertEquals(Status.Code.UNAUTHENTICATED, exception.getStatus().getCode());
        }
        verify(serverCallHandler, never()).startCall(any(), any());

        Metadata headers = headers("xyz");
        interceptor.interceptCall(serverCall, headers, serverCallHandler);
        verify(serverCallHandler).startCall(serverCall, headers);
    }

    private static Metadata headers(String password) {
        Metadata headers = new Metadata();
        if (password != null) {
            headers.put(PASSWORD_KEY, password);
        }
        return headers;
    }
}

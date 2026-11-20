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

package bisq.network.p2p.network;

import bisq.common.UserThread;
import bisq.common.proto.network.NetworkProtoResolver;

import java.net.Socket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionOutOfMemoryTest {
    private final MessageListener messageListener = mock(MessageListener.class);
    private final ConnectionListener connectionListener = mock(ConnectionListener.class);
    private final NetworkProtoResolver networkProtoResolver = mock(NetworkProtoResolver.class);

    @BeforeEach
    void setUp() {
        UserThread.resetForTests();
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
    }

    // A headless node shuts down at an OutOfMemoryError which reaches the uncaught exception handler
    @Test
    void passesOutOfMemoryErrorToTheUncaughtExceptionHandler() throws Exception {
        OutOfMemoryError error = new OutOfMemoryError("test");
        CompletableFuture<Throwable> uncaught = new CompletableFuture<>();
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> uncaught.complete(throwable));
        try {
            connection(new InputStream() {
                @Override
                public int read() {
                    throw error;
                }
            });

            assertSame(error, uncaught.get(5, TimeUnit.SECONDS));
            verify(connectionListener, never()).onDisconnect(any(), any());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        }
    }

    private Connection connection(InputStream inputStream) throws IOException {
        Socket socket = mock(Socket.class);
        when(socket.getInputStream()).thenReturn(inputStream);
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return new Connection(socket, messageListener, connectionListener, null, networkProtoResolver, null);
    }
}

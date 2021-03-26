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

package bisq.core.dao.node.full.rpc;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class BitcoindDaemonTest {
    private BitcoindDaemon daemon;
    private int acceptAnotherCount;
    private CountDownLatch errorHandlerLatch = new CountDownLatch(1);
    private Consumer<Throwable> errorHandler = mock(ThrowableConsumer.class);
    private BitcoindDaemon.BlockListener blockListener = mock(BitcoindDaemon.BlockListener.class);
    private Socket socket = mock(Socket.class);
    private volatile boolean socketClosed;

    @Before
    public void setUp() throws Exception {
        var serverSocket = mock(ServerSocket.class);

        when(serverSocket.accept()).then(invocation -> waitToAccept(() -> {
            if (socketClosed) {
                throw new SocketException();
            }
            return socket;
        }));
        doAnswer((VoidAnswer) invocation -> {
            socketClosed = true;
            acceptAnother(1);
        }).when(serverSocket).close();

        doAnswer((VoidAnswer) invocation -> errorHandlerLatch.countDown()).when(errorHandler).accept(any());

        daemon = new BitcoindDaemon(serverSocket, errorHandler);
        daemon.setBlockListener(blockListener);
    }

    @After
    public void tearDown() {
        daemon.shutdown();
    }

    @Test
    public void testNoBlocksMissedDuringFloodOfIncomingBlocks() throws Exception {
        var latch = new CountDownLatch(1); // to block all the daemon worker threads until shutdown, as if stuck

        doAnswer((VoidAnswer) invocation -> latch.await()).when(blockListener).blockDetected(any());
        when(socket.getInputStream()).then(invocation -> new ByteArrayInputStream("foo".getBytes()));

        acceptAnother(50);
        waitUntilAllAccepted();

        // Unblock all the daemon worker threads and shut down.
        latch.countDown();
        daemon.shutdown();

        verify(blockListener, times(50)).blockDetected("foo");
    }

    @Test
    public void testBlockHashIsTrimmed() throws Exception {
        when(socket.getInputStream()).then(invocation -> new ByteArrayInputStream("\r\nbar \n".getBytes()));

        acceptAnother(1);
        waitUntilAllAccepted();
        daemon.shutdown();

        verify(blockListener).blockDetected("bar");
    }

    @Test
    public void testBrokenSocketRead() throws Exception {
        when(socket.getInputStream()).thenThrow(IOException.class);

        acceptAnother(1);
        errorHandlerLatch.await(5, TimeUnit.SECONDS);

        verify(errorHandler).accept(argThat(t -> t instanceof NotificationHandlerException &&
                t.getCause() instanceof IOException));
    }

    @Test
    public void testRuntimeExceptionInBlockListener() throws Exception {
        daemon.setBlockListener(blockHash -> {
            throw new IndexOutOfBoundsException();
        });
        when(socket.getInputStream()).then(invocation -> new ByteArrayInputStream("foo".getBytes()));

        acceptAnother(1);
        errorHandlerLatch.await(5, TimeUnit.SECONDS);

        verify(errorHandler).accept(argThat(t -> t instanceof NotificationHandlerException &&
                t.getCause() instanceof IndexOutOfBoundsException));
    }


    @Test
    public void testErrorInBlockListener() throws Exception {
        synchronized (this) {
            daemon.setBlockListener(blockHash -> {
                throw new Error();
            });
            when(socket.getInputStream()).then(invocation -> new ByteArrayInputStream("foo".getBytes()));
            acceptAnother(1);
        }
        errorHandlerLatch.await(5, TimeUnit.SECONDS);

        verify(errorHandler).accept(any(Error.class));
    }

    @Test(expected = NotificationHandlerException.class)
    public void testUnknownHost() throws Exception {
        new BitcoindDaemon("[", -1, errorHandler).shutdown();
    }

    private synchronized void acceptAnother(int n) {
        acceptAnotherCount += n;
        notifyAll();
    }

    private synchronized <V> V waitToAccept(Callable<V> onAccept) throws Exception {
        while (acceptAnotherCount == 0) {
            wait();
        }
        var result = onAccept.call();
        acceptAnotherCount--;
        notifyAll();
        return result;
    }

    private synchronized void waitUntilAllAccepted() throws InterruptedException {
        while (acceptAnotherCount > 0) {
            wait();
        }
        notifyAll();
    }

    private interface ThrowableConsumer extends Consumer<Throwable> {
    }

    private interface VoidAnswer extends Answer<Void> {
        void voidAnswer(InvocationOnMock invocation) throws Throwable;

        @Override
        default Void answer(InvocationOnMock invocation) throws Throwable {
            voidAnswer(invocation);
            return null;
        }
    }
}

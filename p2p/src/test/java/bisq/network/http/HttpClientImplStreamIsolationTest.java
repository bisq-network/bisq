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

package bisq.network.http;

import bisq.network.Socks5ProxyProvider;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.UserPasswordAuthentication;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import java.nio.charset.StandardCharsets;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the HTTP and HTTPS requests of {@link HttpClientImpl} against a SOCKS5 server on the loopback interface, which
 * records the handshake, so that no request leaves the host.
 */
class HttpClientImplStreamIsolationTest {
    private FakeSocksServer socksServer;

    @BeforeEach
    void setUp() throws IOException {
        socksServer = new FakeSocksServer();
    }

    @AfterEach
    void tearDown() throws IOException {
        socksServer.close();
    }

    @Test
    void withStreamIsolationEachRequestSendsItsOwnCredentials() throws Exception {
        // As TorNetworkNode.getSocksProxy with stream isolation: a proxy with new credentials for each call
        HttpClientImpl client = new HttpClientImpl(providerOf(() -> isolatedProxy(socksServer.port())));
        client.setBaseUrl("http://example.com/");

        assertEquals("ok", client.get("first", null, null));
        assertEquals("ok", client.get("second", null, null));

        Handshake first = socksServer.nextHandshake();
        Handshake second = socksServer.nextHandshake();
        assertEquals(List.of(2), first.methods());
        assertEquals(List.of(2), second.methods());
        assertNotNull(first.username());
        assertNotEquals(first.username(), second.username());
        // The proxy resolves the name, not the client
        assertEquals(3, first.addressType());
        assertEquals("example.com", first.host());
        assertEquals(80, first.port());
    }

    @Test
    void anHttpsRequestSendsTheCredentialsBeforeTls() throws Exception {
        HttpClientImpl client = new HttpClientImpl(providerOf(() -> isolatedProxy(socksServer.port())));
        client.setBaseUrl("https://example.com/");

        // The fake server does not speak TLS, so the request fails after the SOCKS handshake
        assertThrows(IOException.class, () -> client.get("tx", null, null));

        Handshake handshake = socksServer.nextHandshake();
        assertEquals(List.of(2), handshake.methods());
        assertNotNull(handshake.username());
        assertEquals("example.com", handshake.host());
        assertEquals(443, handshake.port());
    }

    @Test
    void withStreamIsolationAProxyWhichRefusesTheCredentialsFailsTheRequest() throws Exception {
        socksServer.acceptUsernameAndPassword = false;
        HttpClientImpl client = new HttpClientImpl(providerOf(() -> isolatedProxy(socksServer.port())));
        client.setBaseUrl("http://example.com/");

        assertThrows(IOException.class, () -> client.get("tx", null, null));

        // The client did not go on without credentials
        Handshake handshake = socksServer.nextHandshake();
        assertEquals(List.of(2), handshake.methods());
        assertNull(handshake.host());
    }

    @Test
    void withoutStreamIsolationTheRequestStillGoesThroughTheProxyByName() throws Exception {
        HttpClientImpl client = new HttpClientImpl(providerOf(() -> plainProxy(socksServer.port())));
        client.setBaseUrl("http://example.com/");

        assertEquals("ok", client.get("first", null, null));

        Handshake handshake = socksServer.nextHandshake();
        // The SOCKS client of the Java runtime also offers no authentication
        assertTrue(handshake.methods().contains(0));
        assertEquals(3, handshake.addressType());
        assertEquals("example.com", handshake.host());
    }

    private interface ProxySupplier {
        Socks5Proxy get() throws UnknownHostException;
    }

    private static Socks5ProxyProvider providerOf(ProxySupplier proxySupplier) {
        Socks5ProxyProvider provider = mock(Socks5ProxyProvider.class);
        when(provider.getSocks5Proxy()).thenAnswer(invocation -> proxySupplier.get());
        return provider;
    }

    private static Socks5Proxy isolatedProxy(int port) throws UnknownHostException {
        Socks5Proxy proxy = plainProxy(port);
        proxy.setAuthenticationMethod(UserPasswordAuthentication.METHOD_ID,
                new UserPasswordAuthentication(UUID.randomUUID().toString(), ""));
        return proxy;
    }

    private static Socks5Proxy plainProxy(int port) throws UnknownHostException {
        Socks5Proxy proxy = new Socks5Proxy(InetAddress.getLoopbackAddress(), port);
        proxy.resolveAddrLocally(false);
        return proxy;
    }

    private record Handshake(List<Integer> methods, String username, int addressType, String host, int port) {
    }

    /**
     * Accepts username and password authentication when it is offered, connects every request to itself and answers
     * an HTTP request with "ok".
     */
    private static final class FakeSocksServer implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "FakeSocksServer");
            thread.setDaemon(true);
            return thread;
        });
        private final BlockingQueue<Handshake> handshakes = new LinkedBlockingQueue<>();
        private volatile boolean acceptUsernameAndPassword = true;

        private FakeSocksServer() throws IOException {
            executor.submit(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        executor.submit(() -> serve(socket));
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        Handshake nextHandshake() throws InterruptedException {
            Handshake handshake = handshakes.poll(10, TimeUnit.SECONDS);
            assertNotNull(handshake, "No SOCKS handshake");
            return handshake;
        }

        private void serve(Socket socket) {
            List<Integer> methods = new ArrayList<>();
            try (socket) {
                socket.setSoTimeout(10_000);
                DataInputStream input = new DataInputStream(socket.getInputStream());
                OutputStream output = socket.getOutputStream();
                input.readUnsignedByte();
                int numMethods = input.readUnsignedByte();
                for (int i = 0; i < numMethods; i++) {
                    methods.add(input.readUnsignedByte());
                }
                String username = null;
                if (methods.contains(2) && acceptUsernameAndPassword) {
                    output.write(new byte[]{5, 2});
                    output.flush();
                    input.readUnsignedByte();
                    username = readString(input, input.readUnsignedByte());
                    readString(input, input.readUnsignedByte());
                    output.write(new byte[]{1, 0});
                } else if (methods.contains(0)) {
                    output.write(new byte[]{5, 0});
                } else {
                    output.write(new byte[]{5, (byte) 0xFF});
                    output.flush();
                    handshakes.add(new Handshake(methods, null, 0, null, 0));
                    return;
                }
                output.flush();

                input.readUnsignedByte();
                input.readUnsignedByte();
                input.readUnsignedByte();
                int addressType = input.readUnsignedByte();
                String host = addressType == 3 ?
                        readString(input, input.readUnsignedByte()) :
                        InetAddress.getByAddress(input.readNBytes(addressType == 1 ? 4 : 16)).getHostAddress();
                int port = input.readUnsignedShort();
                handshakes.add(new Handshake(methods, username, addressType, host, port));
                output.write(new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
                output.flush();

                if (port == 80) {
                    readHttpRequestHead(input);
                    output.write(("HTTP/1.0 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                }
            } catch (IOException ignore) {
                // The client closes the connection, for example after a failed TLS handshake
            }
        }

        private static String readString(DataInputStream input, int length) throws IOException {
            return new String(input.readNBytes(length), StandardCharsets.US_ASCII);
        }

        // Reads up to the empty line after the headers
        private static void readHttpRequestHead(DataInputStream input) throws IOException {
            int lastFourBytes = 0;
            while (lastFourBytes != 0x0D0A0D0A) {
                lastFourBytes = (lastFourBytes << 8) | input.readUnsignedByte();
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            executor.shutdownNow();
        }
    }
}

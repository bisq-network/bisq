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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the most security-relevant behaviour of {@link HttpClientImpl}: that
 * non-local requests cannot escape Tor.
 *
 * The clearnet HTTP path is exercised against an in-process loopback server so
 * that no actual network I/O leaves the host.
 */
class HttpClientImplFailClosedTest {

    @Test
    void rejectsNonLocalUrlWhenProxyMissing() {
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        client.setBaseUrl("http://example.com/");
        IOException ex = assertThrows(IOException.class,
                () -> client.get("/foo", null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("socks5"),
                "expected fail-closed message, got: " + ex.getMessage());
    }

    @Test
    void rejectsNonLocalUrlEvenWhenIgnoreProxyTrue() {
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        client.setBaseUrl("http://example.com/");
        client.setIgnoreSocks5Proxy(true);
        IOException ex = assertThrows(IOException.class,
                () -> client.get("/foo", null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("non-local"),
                "expected non-local refusal, got: " + ex.getMessage());
    }

    @Test
    void rejectsNonLocalUrlEvenWhenProxyProviderReturnsNull() {
        Socks5ProxyProvider provider = mock(Socks5ProxyProvider.class);
        when(provider.getSocks5Proxy()).thenReturn(null);
        when(provider.getSocks5ProxyHttp()).thenReturn(null);

        HttpClientImpl client = new HttpClientImpl(provider);
        client.setBaseUrl("https://api.example.com/");
        IOException ex = assertThrows(IOException.class,
                () -> client.get("/foo", null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("socks5"),
                "expected fail-closed message, got: " + ex.getMessage());
    }

    @Test
    void doesNotConsultProxyForOnionAddresses_butStillRejectsWithoutProxy() {
        // Onion addresses MUST go via Tor. If no proxy is configured we refuse.
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        client.setBaseUrl("http://expyuzz4wqqyqhjn.onion/");
        assertThrows(IOException.class, () -> client.get("/foo", null, null));
    }

    @Test
    void rejectsInvalidSchemeAtSetBaseUrl() {
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> client.setBaseUrl("file:///etc/passwd"));
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> client.setBaseUrl("javascript:alert(1)"));
    }

    @Test
    void rejectsLocalhostSpoofViaUserinfo() {
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        // http://localhost@evil.com/ would historically have been treated as local
        // by a contains("localhost") check. Now it is rejected outright.
        assertThrows(UrlSafetyChecker.InvalidUrlException.class,
                () -> client.setBaseUrl("http://localhost@evil.com/"));
    }

    @Test
    void rejectsGetParamHostSmuggling() {
        // baseUrl="http://localhost" + param="@evil.com" → effective host = evil.com.
        // Earlier code parsed only baseUrl and would have treated this as "local";
        // doRequest now re-parses baseUrl+param for GET so the effective host is
        // checked. The UserInfo guard in UrlSafetyChecker rejects the result.
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        client.setBaseUrl("http://localhost");
        assertThrows(IOException.class, () -> client.get("@evil.com/foo", null, null));
    }

    @Test
    void rejectsGetParamSchemeSmuggling() {
        // Param starting with " #" or " ?" doesn't change host, but starting with
        // "@" or with characters that re-enter the authority section would. Catch
        // any malformed concatenation, not just the userinfo flavour.
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        client.setBaseUrl("http://localhost");
        assertThrows(IOException.class, () -> client.get(" attacker.com", null, null));
    }

    @Test
    void rejectsLocalhostSpoofViaPath() throws IOException {
        // A URL whose path contains "localhost" must NOT bypass Tor.
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        client.setBaseUrl("http://attacker.com/");
        IOException ex = assertThrows(IOException.class,
                () -> client.get("/localhost/data", null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("socks5"));
    }

    @Test
    void localhostRequestSucceedsWithoutProxy() throws Exception {
        try (TinyHttpServer server = TinyHttpServer.start("hello")) {
            HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
            client.setBaseUrl("http://127.0.0.1:" + server.port() + "/");
            String body = client.get("foo", null, null);
            assertEquals("hello", body);
        }
    }

    @Test
    void localhostRequestSucceedsEvenWithBrokenProxyProvider() throws Exception {
        // ignoreSocks5Proxy is unnecessary for loopback URLs, but if a caller
        // sets it the local request must still go through.
        Socks5ProxyProvider provider = mock(Socks5ProxyProvider.class);
        Socks5Proxy proxy = mock(Socks5Proxy.class);
        when(provider.getSocks5Proxy()).thenReturn(proxy);

        try (TinyHttpServer server = TinyHttpServer.start("ok")) {
            HttpClientImpl client = new HttpClientImpl(provider);
            client.setBaseUrl("http://localhost:" + server.port() + "/");
            client.setIgnoreSocks5Proxy(true);
            String body = client.get("foo", null, null);
            assertEquals("ok", body);
        }
    }

    @Test
    void responseSizeIsCapped() throws Exception {
        // Build a payload larger than the cap and verify the client refuses to
        // buffer it. We send the cap + 1 KiB so the server stays bounded.
        long cap = HttpClientImpl.MAX_RESPONSE_BYTES;
        byte[] payload = new byte[(int) cap + 1024];
        for (int i = 0; i < payload.length; i++) payload[i] = 'A';

        try (TinyHttpServer server = TinyHttpServer.startBytes(payload)) {
            HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
            client.setBaseUrl("http://127.0.0.1:" + server.port() + "/");
            IOException ex = assertThrows(IOException.class,
                    () -> client.get("foo", null, null));
            // Make sure we caught the cap, not some unrelated IOException.
            String msg = rootCauseMessage(ex);
            assertNotNull(msg);
            assertTrue(msg.contains("exceeded") && msg.contains(String.valueOf(cap)),
                    "expected size-cap message, got: " + msg);
        }
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage();
    }

    /**
     * Minimal HTTP/1.0 server bound to loopback. Returns a single response then
     * closes the socket. Sufficient for asserting that local URLs reach the
     * stack and that response handling honours its bounds.
     */
    static final class TinyHttpServer implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService exec;

        private TinyHttpServer(ServerSocket server, ExecutorService exec) {
            this.server = server;
            this.exec = exec;
        }

        static TinyHttpServer start(String body) throws IOException {
            return startBytes(body.getBytes(StandardCharsets.UTF_8));
        }

        static TinyHttpServer startBytes(byte[] body) throws IOException {
            ServerSocket server = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress());
            ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "TinyHttpServer");
                t.setDaemon(true);
                return t;
            });
            exec.submit(() -> {
                try (Socket s = server.accept()) {
                    // Bound the test: a malformed client must not hang the suite.
                    s.setSoTimeout(5_000);
                    InputStream in = s.getInputStream();
                    // Drain request line + headers, capped at 64 KiB so a runaway
                    // client cannot push us into unbounded buffering.
                    byte[] buf = new byte[4096];
                    int totalRead = 0;
                    final int requestCap = 64 * 1024;
                    // Track the \r\n\r\n header terminator across read() boundaries
                    // so a split delivery doesn't leave us waiting for the timeout.
                    int read;
                    int matched = 0;
                    while (totalRead < requestCap && (read = in.read(buf)) > 0) {
                        totalRead += read;
                        for (int i = 0; i < read && matched < 4; i++) {
                            byte b = buf[i];
                            switch (matched) {
                                case 0:
                                    matched = b == '\r' ? 1 : 0;
                                    break;
                                case 1:
                                    matched = b == '\n' ? 2 : (b == '\r' ? 1 : 0);
                                    break;
                                case 2:
                                    matched = b == '\r' ? 3 : 0;
                                    break;
                                case 3:
                                    matched = b == '\n' ? 4 : (b == '\r' ? 1 : 0);
                                    break;
                                default:
                                    break;
                            }
                        }
                        if (matched == 4) break;
                    }
                    String headers = "HTTP/1.0 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                    s.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                    s.getOutputStream().write(body);
                    s.getOutputStream().flush();
                } catch (IOException ignore) {
                }
                return null;
            });
            return new TinyHttpServer(server, exec);
        }

        int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            try {
                server.close();
            } finally {
                exec.shutdownNow();
            }
        }
    }
}

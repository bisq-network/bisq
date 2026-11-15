package bisq.network.http;

import bisq.network.Socks5ProxyProvider;

import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientImplSensitiveTest {
    @Test
    void redactsRequestResponseAndExceptionsForSuccessAndFailure() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpClientImpl.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/private-endpoint", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            assertTrue(new String(request, StandardCharsets.UTF_8).contains("private-trade-123"));
            assertEquals("Bearer tk_private", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "private-server-response tk_private".getBytes(StandardCharsets.UTF_8);
            int status = exchange.getRequestURI().getPath().endsWith("/error") ? 403 : 200;
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        client.setSensitive(true);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/private-endpoint";
            client.setBaseUrl(baseUrl);
            assertEquals("private-server-response tk_private",
                    client.post("private-trade-123", "Authorization", "Bearer tk_private"));
            client.setBaseUrl(baseUrl + "/error");
            IOException error = assertThrows(IOException.class, () ->
                    client.post("private-trade-123", "Authorization", "Bearer tk_private"));
            assertEquals("Sensitive HTTP request failed", error.getMessage());
            assertNull(error.getCause());
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            for (String secret : new String[]{"private-trade-123", "private-server-response", "tk_private", "private-endpoint"}) {
                assertFalse(logs.contains(secret));
                assertFalse(client.toString().contains(secret));
            }
            assertFalse(appender.list.isEmpty());
        } finally {
            client.shutDown();
            server.stop(0);
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void redactsRoutingFailuresWithoutBypassingTor() {
        HttpClientImpl client = new HttpClientImpl((Socks5ProxyProvider) null);
        client.setSensitive(true);
        client.setBaseUrl("https://private-endpoint.example/");
        IOException error = assertThrows(IOException.class, () -> client.post("private-trade-123", null, null));
        assertEquals("Sensitive HTTP request failed", error.getMessage());
        assertNull(error.getCause());
        assertFalse(client.hasPendingRequest());
    }
}

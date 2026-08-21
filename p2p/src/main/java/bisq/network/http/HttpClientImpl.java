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

import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.util.Utilities;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import javax.inject.Inject;
import javax.inject.Named;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * HTTP client used for any external query (price feed, fee estimation, BSQ
 * explorer, mempool broadcast, XMR proof, …).
 *
 * Hardening invariants enforced here:
 *   - URL must parse as a valid http/https URI with a non-empty host (no file://,
 *     no userinfo, no schemes outside http/https).
 *   - Requests addressed to a non-local destination MUST go through the SOCKS5
 *     (Tor) proxy. If the proxy is missing the request is rejected — we
 *     fail-closed rather than leaking traffic to the clearnet.
 *   - "Local" means a loopback IP literal or the literal hostname "localhost"
 *     (or IPv6 equivalents). Hostnames that would require a DNS lookup are not
 *     treated as local because answering would leak the lookup.
 *   - The {@link #setIgnoreSocks5Proxy(boolean)} escape hatch is honoured only
 *     when the URL is local. Setting the flag for a non-local URL is a bug and
 *     is refused at request time.
 *   - Conservative timeouts (80 s connect / 60 s read) and a 512 KiB response
 *     body cap bound the damage of a malicious or misbehaving server.
 *   - Bodies are decoded as UTF-8; size accounting is byte-exact.
 */
@Slf4j
public class HttpClientImpl implements HttpClient {
    // Connect=80s covers Tor circuit setup + onion-service rendezvous on a cold
    // start; read=60s is per-byte inactivity, generous for any healthy server
    // once the circuit is up.
    static final int CONNECT_TIMEOUT_SEC = 80;
    static final int READ_TIMEOUT_SEC = 60;
    // 512 KiB is well above any legitimate response from price feeds, fee
    // estimators, mempool/explorer JSON, or XMR proof endpoints. A larger cap
    // just widens the memory-exhaustion window without enabling any real use.
    static final long MAX_RESPONSE_BYTES = 512L * 1024;

    @Nullable
    private final Socks5ProxyProvider socks5ProxyProvider;
    private final boolean allowLanForHttpRequests;
    private final boolean allowClearnetHttpRequests;
    @Nullable
    private volatile HttpURLConnection connection;
    @Nullable
    private volatile CloseableHttpClient closeableHttpClient;

    @Getter
    private volatile String baseUrl;
    private volatile boolean ignoreSocks5Proxy;
    @Getter
    private final String uid;
    private final AtomicBoolean hasPendingRequest = new AtomicBoolean(false);

    @Inject
    public HttpClientImpl(@Nullable Socks5ProxyProvider socks5ProxyProvider,
                          @Named(Config.ALLOW_LAN_FOR_HTTP_REQUESTS) boolean allowLanForHttpRequests,
                          @Named(Config.ALLOW_CLEARNET_HTTP_REQUESTS) boolean allowClearnetHttpRequests) {
        this.socks5ProxyProvider = socks5ProxyProvider;
        this.allowLanForHttpRequests = allowLanForHttpRequests;
        this.allowClearnetHttpRequests = allowClearnetHttpRequests;
        if (allowClearnetHttpRequests) {
            log.warn("allowClearnetHttpRequests is enabled: HTTP requests to non-local destinations will " +
                    "bypass Tor and leak your IP address. Use only for development/testing.");
        }
        uid = UUID.randomUUID().toString();
    }

    public HttpClientImpl(@Nullable Socks5ProxyProvider socks5ProxyProvider) {
        // Test/legacy entry point. Defaults to strict loopback-only.
        this(socks5ProxyProvider, false, false);
    }

    public HttpClientImpl(String baseUrl) {
        this.socks5ProxyProvider = null;
        this.allowLanForHttpRequests = false;
        this.allowClearnetHttpRequests = false;
        setBaseUrl(baseUrl);
        uid = UUID.randomUUID().toString();
    }

    @Override
    public void setBaseUrl(String baseUrl) {
        // Validate up-front so misconfiguration surfaces at injection time, not at
        // first request time.
        UrlSafetyChecker.parseAndValidate(baseUrl);
        this.baseUrl = baseUrl;
    }

    @Override
    public void setIgnoreSocks5Proxy(boolean ignoreSocks5Proxy) {
        this.ignoreSocks5Proxy = ignoreSocks5Proxy;
    }

    @Override
    public void shutDown() {
        try {
            HttpURLConnection c = connection;
            if (c != null) {
                try {
                    InputStream is = c.getInputStream();
                    if (is != null) is.close();
                } catch (IOException ignore) {
                }
                c.disconnect();
                connection = null;
            }
            CloseableHttpClient client = closeableHttpClient;
            if (client != null) {
                client.close();
                closeableHttpClient = null;
            }
        } catch (IOException ignore) {
        }
    }

    @Override
    public boolean hasPendingRequest() {
        return hasPendingRequest.get();
    }

    @Override
    public String get(String param,
                      @Nullable String headerKey,
                      @Nullable String headerValue) throws IOException {
        return doRequest(param, HttpMethod.GET, headerKey, headerValue);
    }

    @Override
    public String post(String param,
                       @Nullable String headerKey,
                       @Nullable String headerValue) throws IOException {
        return doRequest(param, HttpMethod.POST, headerKey, headerValue);
    }

    private String doRequest(String param,
                             HttpMethod httpMethod,
                             @Nullable String headerKey,
                             @Nullable String headerValue) throws IOException {
        checkNotNull(baseUrl, "baseUrl must be set before calling doRequest");
        checkArgument(hasPendingRequest.compareAndSet(false, true),
                "We got called on the same HttpClient again while a request is still open.");
        try {
            // Validate the *effective* URL the network stack will see. For GET we
            // append param to baseUrl, so a baseUrl with no trailing slash plus a
            // param starting with '@', '?', or '#' could shift the effective host
            // (e.g. baseUrl="http://localhost" + param="@evil.com" → host=evil.com).
            // Re-parsing the concatenation closes that bypass: parseAndValidate
            // rejects userinfo, and the host we hand to isLocal is the real target.
            String effectiveSpec = httpMethod == HttpMethod.GET ? baseUrl + param : baseUrl;
            URI uri;
            try {
                uri = UrlSafetyChecker.parseAndValidate(effectiveSpec);
            } catch (UrlSafetyChecker.InvalidUrlException e) {
                // Surface URL-validation failures as IOException so callers (which
                // declare `throws IOException`) handle them like any other request
                // failure rather than via runtime-exception propagation.
                throw new IOException(e.getMessage());
            }
            boolean local = UrlSafetyChecker.isLocal(uri, allowLanForHttpRequests);
            boolean onion = UrlSafetyChecker.isOnion(uri);
            Socks5Proxy socks5Proxy = getSocks5Proxy(socks5ProxyProvider);

            if (local) {
                // localhost/loopback only — direct connection is correct.
                return requestWithoutProxy(baseUrl, param, httpMethod, headerKey, headerValue);
            }

            if (ignoreSocks5Proxy) {
                // Caller explicitly opted out of Tor for a non-local URL. Refuse —
                // this would leak the request and historically has been a source of
                // bugs (e.g. a misconfigured XMR proof service URL).
                throw new IOException("ignoreSocks5Proxy is only allowed for local URLs, " +
                        "refusing to send " + httpMethod + " to non-local " + baseUrl);
            }

            if (socks5Proxy != null) {
                // Tor available — always prefer it for non-local destinations,
                // even when allowClearnetHttpRequests=true. The flag is a
                // last-resort fallback, not a global Tor disable.
                return doRequestWithProxy(baseUrl, param, httpMethod, socks5Proxy, headerKey, headerValue);
            }

            // No proxy available.
            if (onion) {
                // Onion hosts are only reachable through Tor; a direct attempt
                // would leak the lookup to the system DNS resolver and fail.
                throw new IOException("No SOCKS5 proxy available, refusing to send "
                        + httpMethod + " to onion host " + baseUrl);
            }

            if (allowClearnetHttpRequests || DevEnv.isDevMode()) {
                // Dev/testing escape hatch: send directly without Tor.
                log.info("No SOCKS5 proxy available; sending {} clearnet (no Tor) to non-local {} " +
                                "because allowClearnetHttpRequests=true",
                        httpMethod, baseUrl);
                return requestWithoutProxy(baseUrl, param, httpMethod, headerKey, headerValue);
            }

            throw new IOException("No SOCKS5 proxy available, refusing to send "
                    + httpMethod + " to non-local " + baseUrl);
        } finally {
            hasPendingRequest.set(false);
        }
    }

    private String requestWithoutProxy(String baseUrl,
                                       String param,
                                       HttpMethod httpMethod,
                                       @Nullable String headerKey,
                                       @Nullable String headerValue) throws IOException {
        long ts = System.currentTimeMillis();
        log.debug("requestWithoutProxy: URL={}, param={}, httpMethod={}", baseUrl, param, httpMethod);
        try {
            String spec = httpMethod == HttpMethod.GET ? baseUrl + param : baseUrl;
            // URI.toURL forces well-formed URLs and rejects characters that the
            // legacy URL constructor accepts silently.
            URL url = new URI(spec).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            connection = conn;
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod(httpMethod.name());
            conn.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SEC));
            conn.setReadTimeout((int) TimeUnit.SECONDS.toMillis(READ_TIMEOUT_SEC));
            conn.setRequestProperty("User-Agent", "bisq/" + Version.VERSION);
            if (headerKey != null && headerValue != null) {
                conn.setRequestProperty(headerKey, headerValue);
            }

            if (httpMethod == HttpMethod.POST) {
                conn.setDoOutput(true);
                conn.getOutputStream().write(param.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (isSuccess(responseCode)) {
                String response = readBoundedUtf8(conn.getInputStream());
                log.debug("Response from {} with param {} took {} ms. Data size:{}, response: {}",
                        baseUrl,
                        param,
                        System.currentTimeMillis() - ts,
                        Utilities.readableFileSize(response.getBytes(StandardCharsets.UTF_8).length),
                        Utilities.toTruncatedString(response));
                return response;
            } else {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    String error = readBoundedUtf8(errorStream);
                    errorStream.close();
                    log.info("Received errorMsg '{}' with responseCode {} from {}. Response took: {} ms. param: {}",
                            error,
                            responseCode,
                            baseUrl,
                            System.currentTimeMillis() - ts,
                            param);
                    throw new HttpException(error, responseCode);
                } else {
                    log.info("Response with responseCode {} from {}. Response took: {} ms. param: {}",
                            responseCode,
                            baseUrl,
                            System.currentTimeMillis() - ts,
                            param);
                    throw new HttpException("Request failed", responseCode);
                }
            }
        } catch (Exception e) {
            // HttpException ends up here too; callers retrieve it via getCause().
            throw new IOException("Direct request to " + baseUrl + " failed: " + rootCauseMessage(e), e);
        } finally {
            HttpURLConnection c = connection;
            if (c != null) {
                try {
                    InputStream is = c.getInputStream();
                    if (is != null) is.close();
                } catch (Throwable ignore) {
                }
                try {
                    c.disconnect();
                } catch (Throwable ignore) {
                }
                connection = null;
            }
        }
    }

    private String doRequestWithProxy(String baseUrl,
                                      String param,
                                      HttpMethod httpMethod,
                                      Socks5Proxy socks5Proxy,
                                      @Nullable String headerKey,
                                      @Nullable String headerValue) throws IOException {
        long ts = System.currentTimeMillis();
        log.debug("doRequestWithProxy: baseUrl={}, param={}, httpMethod={}", baseUrl, param, httpMethod);

        // Register socket factories that pass the unresolved hostname through to
        // the SOCKS proxy so DNS resolution happens on the Tor exit, never on the
        // client. See SocksConnectionSocketFactory / SocksSSLConnectionSocketFactory.
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new SocksConnectionSocketFactory())
                .register("https", new SocksSSLConnectionSocketFactory(SSLContexts.createSystemDefault())).build();

        // Belt-and-braces: even though the registered SocketFactories never
        // consult DnsResolver, force any accidental call to fail rather than
        // silently leak a lookup. Apache requires a non-null result; we return
        // an unspecified address that cannot route.
        PoolingHttpClientConnectionManager cm = socks5Proxy.resolveAddrLocally() ?
                new PoolingHttpClientConnectionManager(reg) :
                new PoolingHttpClientConnectionManager(reg, new FakeDnsResolver());
        try {
            // Mirror the timeouts and redirect policy of the direct path:
            //   - Apache defaults to following 3xx for GET/HEAD, which would let a
            //     validated host redirect to an unvalidated target and bypass
            //     UrlSafetyChecker. Disable redirect handling.
            //   - Apache defaults to no socket / connect timeout. Without this a
            //     stalled circuit pins the request until the JVM gives up.
            RequestConfig requestConfig = RequestConfig.custom()
                    .setRedirectsEnabled(false)
                    .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SEC))
                    .setConnectionRequestTimeout((int) TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SEC))
                    .setSocketTimeout((int) TimeUnit.SECONDS.toMillis(READ_TIMEOUT_SEC))
                    .build();
            CloseableHttpClient client = checkNotNull(HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultRequestConfig(requestConfig)
                    .disableRedirectHandling()
                    .build());
            closeableHttpClient = client;
            InetSocketAddress socksAddress = new InetSocketAddress(socks5Proxy.getInetAddress(), socks5Proxy.getPort());

            // remove me: Use this to test with system-wide Tor proxy, or change port for another proxy.
            // InetSocketAddress socksAddress = new InetSocketAddress("127.0.0.1", 9050);

            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksAddress);

            HttpUriRequest request = getHttpUriRequest(httpMethod, baseUrl, param);
            if (headerKey != null && headerValue != null) {
                request.setHeader(headerKey, headerValue);
            }

            try (CloseableHttpResponse httpResponse = client.execute(request, context)) {
                int statusCode = httpResponse.getStatusLine().getStatusCode();
                // Per HttpResponse contract, getEntity() may return null for bodyless
                // responses (204 No Content, 304 Not Modified, HEAD, …).
                HttpEntity entity = httpResponse.getEntity();
                String response = entity != null ? readBoundedUtf8(entity.getContent()) : "";
                if (isSuccess(statusCode)) {
                    log.debug("Response from {} took {} ms. Data size:{}, response: {}, param: {}",
                            baseUrl,
                            System.currentTimeMillis() - ts,
                            Utilities.readableFileSize(response.getBytes(StandardCharsets.UTF_8).length),
                            Utilities.toTruncatedString(response),
                            param);
                    return response;
                } else {
                    log.info("Received errorMsg '{}' with statusCode {} from {}. Response took: {} ms. param: {}",
                            response,
                            statusCode,
                            baseUrl,
                            System.currentTimeMillis() - ts,
                            param);
                    throw new HttpException(response, statusCode);
                }
            }
        } catch (Exception e) {
            // HttpException ends up here too; callers retrieve it via getCause().
            throw new IOException("Request via SOCKS proxy to " + baseUrl + " failed: " + rootCauseMessage(e), e);
        } finally {
            CloseableHttpClient client = closeableHttpClient;
            if (client != null) {
                try {
                    client.close();
                } catch (Throwable ignore) {
                }
                closeableHttpClient = null;
            }
        }
    }

    private HttpUriRequest getHttpUriRequest(HttpMethod httpMethod, String baseUrl, String param)
            throws UnsupportedEncodingException {
        switch (httpMethod) {
            case GET:
                return new HttpGet(baseUrl + param);
            case POST:
                HttpPost httpPost = new HttpPost(baseUrl);
                HttpEntity httpEntity = new StringEntity(param);
                httpPost.setEntity(httpEntity);
                return httpPost;

            default:
                throw new IllegalArgumentException("HttpMethod not supported: " + httpMethod);
        }
    }

    @Nullable
    private Socks5Proxy getSocks5Proxy(@Nullable Socks5ProxyProvider socks5ProxyProvider) {
        if (socks5ProxyProvider == null) {
            return null;
        }

        // We use the custom socks5ProxyHttp.
        Socks5Proxy socks5Proxy = socks5ProxyProvider.getSocks5ProxyHttp();
        if (socks5Proxy != null) {
            return socks5Proxy;
        }

        // If not set we request socks5ProxyProvider.getSocks5Proxy()
        // which delivers the btc proxy if set, otherwise the internal proxy.
        return socks5ProxyProvider.getSocks5Proxy();
    }

    static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null ? msg : cur.getClass().getSimpleName();
    }

    /**
     * Reads {@code inputStream} as UTF-8, refusing more than {@link #MAX_RESPONSE_BYTES}
     * bytes. The cap defends against memory-exhaustion via an unbounded server response.
     */
    static String readBoundedUtf8(InputStream inputStream) throws IOException {
        BoundedInputStream bounded = new BoundedInputStream(inputStream, MAX_RESPONSE_BYTES);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private final long max;
        private long count;

        BoundedInputStream(InputStream delegate, long max) {
            this.delegate = delegate;
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b == -1) return -1;
            if (++count > max) {
                throw new IOException("Response exceeded " + max + " bytes");
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n == -1) return -1;
            count += n;
            if (count > max) {
                throw new IOException("Response exceeded " + max + " bytes");
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    @Override
    public String toString() {
        return "HttpClientImpl{" +
                "\n     socks5ProxyProvider=" + socks5ProxyProvider +
                ",\n     baseUrl='" + baseUrl + '\'' +
                ",\n     ignoreSocks5Proxy=" + ignoreSocks5Proxy +
                ",\n     uid='" + uid + '\'' +
                ",\n     connection=" + connection +
                ",\n     httpclient=" + closeableHttpClient +
                "\n}";
    }
}

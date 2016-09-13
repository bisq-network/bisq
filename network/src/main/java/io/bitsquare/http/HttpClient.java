package io.bitsquare.http;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bitsquare.network.Socks5ProxyProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import static com.google.common.base.Preconditions.checkNotNull;


public class HttpClient {
    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

    private final Socks5ProxyProvider socks5ProxyProvider;
    private String baseUrl;
    private boolean ignoreSocks5Proxy;

    @Inject
    public HttpClient(Socks5ProxyProvider socks5ProxyProvider) {
        this.socks5ProxyProvider = socks5ProxyProvider;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setIgnoreSocks5Proxy(boolean ignoreSocks5Proxy) {
        this.ignoreSocks5Proxy = ignoreSocks5Proxy;
    }

    public String requestWithGET(String param) throws IOException, HttpException {
        checkNotNull(baseUrl, "baseUrl must be set before calling requestWithGET");

        // We use the custom socks5ProxyHttp. If not set we request socks5ProxyProvider.getSocks5ProxyBtc()
        // which delivers the btc proxy if set, otherwise the internal proxy.
        Socks5Proxy socks5Proxy = socks5ProxyProvider.getSocks5ProxyHttp();
        if (socks5Proxy == null)
            socks5Proxy = socks5ProxyProvider.getSocks5Proxy();

        if (ignoreSocks5Proxy) {
            log.debug("Use clear net for HttpClient because ignoreSocks5Proxy is set to true");
            return requestWithGETNoProxy(param);
        } else if (socks5Proxy == null) {
            log.debug("Use clear net for HttpClient because socks5Proxy is null");
            return requestWithGETNoProxy(param);
        } else {
            log.debug("Use socks5Proxy for HttpClient: " + socks5Proxy);
            return requestWithGETProxy(param, socks5Proxy);
        }
    }

    /**
     * Make an HTTP Get request directly (not routed over socks5 proxy).
     */
    private String requestWithGETNoProxy(String param) throws IOException, HttpException {
        HttpURLConnection connection = null;
        try {
            log.debug("Executing HTTP request " + baseUrl + param + " proxy: none.");
            URL url = new URL(baseUrl + param);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);

            if (connection.getResponseCode() == 200) {
                return convertInputStreamToString(connection.getInputStream());
            } else {
                String error = convertInputStreamToString(connection.getErrorStream());
                connection.getErrorStream().close();
                throw new HttpException(error);
            }
        } catch (Throwable t) {
            log.debug("Error at requestWithGETNoProxy: " + t.getMessage());
            throw new IOException(t);
        } finally {
            if (connection != null)
                connection.getInputStream().close();
        }
    }

    /**
     * Make an HTTP Get request routed over socks5 proxy.
     */
    private String requestWithGETProxy(String param, Socks5Proxy socks5Proxy) throws IOException, HttpException {
        log.debug("requestWithGETProxy param=" + param);
        // This code is adapted from:
        //  http://stackoverflow.com/a/25203021/5616248

        // Register our own SocketFactories to override createSocket() and connectSocket().
        // connectSocket does NOT resolve hostname before passing it to proxy.
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new SocksConnectionSocketFactory())
                .register("https", new SocksSSLConnectionSocketFactory(SSLContexts.createSystemDefault())).build();

        // Use FakeDNSResolver if not resolving DNS locally.
        // This prevents a local DNS lookup (which would be ignored anyway)
        PoolingHttpClientConnectionManager cm = socks5Proxy.resolveAddrLocally() ?
                new PoolingHttpClientConnectionManager(reg) :
                new PoolingHttpClientConnectionManager(reg, new FakeDnsResolver());
        CloseableHttpClient httpclient = HttpClients.custom().setConnectionManager(cm).build();
        try {
            InetSocketAddress socksaddr = new InetSocketAddress(socks5Proxy.getInetAddress(), socks5Proxy.getPort());

            // remove me: Use this to test with system-wide Tor proxy, or change port for another proxy.
            // InetSocketAddress socksaddr = new InetSocketAddress("127.0.0.1", 9050);

            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksaddr);

            HttpGet request = new HttpGet(baseUrl + param);

            log.debug("Executing request " + request + " proxy: " + socksaddr);
            CloseableHttpResponse response = httpclient.execute(request, context);
            try {
                InputStream stream = response.getEntity().getContent();
                String buf = convertInputStreamToString(stream);
                return buf;
            } finally {
                response.close();
            }
        } catch (Throwable t) {
            log.debug("Error at requestWithGETProxy: " + t.getMessage());
            throw new IOException(t);
        } finally {
            httpclient.close();
        }
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        return "HttpClient{" +
                "socks5ProxyProvider=" + socks5ProxyProvider +
                ", baseUrl='" + baseUrl + '\'' +
                ", ignoreSocks5Proxy=" + ignoreSocks5Proxy +
                '}';
    }
}

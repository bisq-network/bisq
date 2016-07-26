package io.bitsquare.http;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
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
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HttpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);
    
    private final String baseUrl;
    private final Socks5Proxy proxy;

    public HttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.proxy = null;
    }
    
    public HttpClient(Socks5Proxy proxy, String baseUrl) {
        this.baseUrl = baseUrl;
        this.proxy = proxy;
    }

    public String requestWithGET(String param) throws IOException, HttpException {
        return proxy != null ? requestWithGETProxy(param) : requestWithGETNoProxy(param);
    }

    /**
     * Make an HTTP Get request directly (not routed over socks5 proxy).
     */
    private String requestWithGETNoProxy(String param) throws IOException, HttpException {
        HttpURLConnection connection = null;
        try {
            log.info( "Executing HTTP request " + baseUrl + param + " proxy: none.");
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
        } finally {
            if (connection != null)
                connection.getInputStream().close();
        }
    }
    
    /**
     * Make an HTTP Get request routed over socks5 proxy.
     */
    private String requestWithGETProxy(String param) throws IOException, HttpException {
        // This code is adapted from:
        //  http://stackoverflow.com/a/25203021/5616248
        
        // Register our own SocketFactories to override createSocket() and connectSocket().
        // connectSocket does NOT resolve hostname before passing it to proxy.
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory> create()
                .register("http", new SocksConnectionSocketFactory())
                .register("https", new SocksSSLConnectionSocketFactory(SSLContexts.createSystemDefault())).build();
                
        // Use FakeDNSResolver if not resolving DNS locally.
        // This prevents a local DNS lookup (which would be ignored anyway)
        PoolingHttpClientConnectionManager cm = proxy != null && proxy.resolveAddrLocally() ?
                                                    new PoolingHttpClientConnectionManager(reg) :
                                                    new PoolingHttpClientConnectionManager(reg, new FakeDnsResolver());
        CloseableHttpClient httpclient = HttpClients.custom().setConnectionManager(cm).build();
        try {
            InetSocketAddress socksaddr = new InetSocketAddress(proxy.getInetAddress(), proxy.getPort());
            
            // remove me: Use this to test with system-wide Tor proxy, or change port for another proxy.
            // InetSocketAddress socksaddr = new InetSocketAddress("127.0.0.1", 9050);
            
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksaddr);
    
            HttpGet request = new HttpGet(baseUrl + param);
    
            log.info( "Executing request " + request + " proxy: " + socksaddr);
            CloseableHttpResponse response = httpclient.execute(request, context);
            try {
                InputStream stream = response.getEntity().getContent();
                String buf = convertInputStreamToString(stream);
                return buf;
            } finally {
                response.close();
            }
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
                "baseUrl='" + baseUrl + '\'' +
                '}';
    }
}

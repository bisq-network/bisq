package io.bisq.core.provider;

import io.bisq.network.http.HttpClient;

import java.io.Serializable;

public abstract class HttpClientProvider implements Serializable {
    protected final HttpClient httpClient;

    public HttpClientProvider(HttpClient httpClient, String baseUrl) {
        this(httpClient, baseUrl, false);
    }

    public HttpClientProvider(HttpClient httpClient, String baseUrl, boolean ignoreSocks5Proxy) {
        this.httpClient = httpClient;

        httpClient.setBaseUrl(baseUrl);

        httpClient.setIgnoreSocks5Proxy(ignoreSocks5Proxy);
    }
}

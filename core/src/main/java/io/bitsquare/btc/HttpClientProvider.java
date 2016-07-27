package io.bitsquare.btc;

import io.bitsquare.http.HttpClient;
import io.bitsquare.user.Preferences;

import java.io.Serializable;

public abstract class HttpClientProvider implements Serializable {
    protected final HttpClient httpClient;

    public HttpClientProvider(HttpClient httpClient, Preferences preferences, String baseUrl) {
        this(httpClient, preferences, baseUrl, false);
    }

    public HttpClientProvider(HttpClient httpClient, Preferences preferences, String baseUrl, boolean ignoreSocks5Proxy) {
        this.httpClient = httpClient;

        httpClient.setBaseUrl(baseUrl);

        httpClient.setIgnoreSocks5Proxy(ignoreSocks5Proxy || !preferences.getUseTorForHttpRequests());
        
        if (!ignoreSocks5Proxy) {
            preferences.useTorForHttpRequestsProperty().addListener((observable, oldValue, newValue) -> {
                httpClient.setIgnoreSocks5Proxy(!newValue);
            });
        }
    }
}

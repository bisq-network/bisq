/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */
package io.bisq.core.provider;

import io.bisq.network.http.HttpClient;

public abstract class HttpClientProvider {
    protected final HttpClient httpClient;

    public HttpClientProvider(HttpClient httpClient, String baseUrl) {
        this(httpClient, baseUrl, false);
    }

    public HttpClientProvider(HttpClient httpClient, String baseUrl, @SuppressWarnings("SameParameterValue") boolean ignoreSocks5Proxy) {
        this.httpClient = httpClient;

        httpClient.setBaseUrl(baseUrl);

        httpClient.setIgnoreSocks5Proxy(ignoreSocks5Proxy);
    }
}

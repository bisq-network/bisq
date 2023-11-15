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

package bisq.core.provider.price;

import bisq.core.provider.HttpClientProvider;

import bisq.network.http.HttpClient;
import bisq.network.p2p.P2PService;

import bisq.common.app.Version;

import com.google.gson.Gson;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceProvider extends HttpClientProvider {

    private boolean shutDownRequested;

    // Do not use Guice here as we might create multiple instances
    public PriceProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl, false);
    }

    public PricenodeDto getAll() throws IOException {
        if (shutDownRequested) {
            return new PricenodeDto();
        }

        String hsVersion = "";
        if (P2PService.getMyNodeAddress() != null)
            hsVersion = P2PService.getMyNodeAddress().getHostName().length() > 22 ? ", HSv3" : ", HSv2";

        String json = httpClient.get("getAllMarketPrices", "User-Agent", "bisq/"
                + Version.VERSION + hsVersion);

        return new Gson().fromJson(json, PricenodeDto.class);
    }

    public String getBaseUrl() {
        return httpClient.getBaseUrl();
    }

    public void shutDown() {
        shutDownRequested = true;
        httpClient.shutDown();
    }
}

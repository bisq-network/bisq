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

package bisq.core.provider;

import bisq.network.Socks5ProxyProvider;
import bisq.network.http.HttpClientImpl;

import bisq.common.app.Version;
import bisq.common.config.Config;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

@Singleton
public class MempoolHttpClient extends HttpClientImpl {
    @Inject
    public MempoolHttpClient(@Nullable Socks5ProxyProvider socks5ProxyProvider,
                             @Named(Config.ALLOW_LAN_FOR_HTTP_REQUESTS) boolean allowLanForHttpRequests,
                             @Named(Config.ALLOW_CLEARNET_HTTP_REQUESTS) boolean allowClearnetHttpRequests) {
        super(socks5ProxyProvider, allowLanForHttpRequests, allowClearnetHttpRequests);
    }

    // returns JSON of the transaction details
    public String getTxDetails(String txId) throws IOException {
        super.shutDown(); // close any prior incomplete request
        String api = "/" + txId;
        return get(api, "User-Agent", "bisq/" + Version.VERSION);
    }


    public CompletableFuture<String> requestTxAsHex(String txId) {
        super.shutDown(); // close any prior incomplete request

        return CompletableFuture.supplyAsync(() -> {
            String api = "/" + txId + "/hex";
            try {
                return get(api, "User-Agent", "bisq/" + Version.VERSION);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

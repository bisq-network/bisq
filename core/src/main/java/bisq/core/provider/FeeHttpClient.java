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

import bisq.common.config.Config;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import javax.annotation.Nullable;

@Singleton
public class FeeHttpClient extends HttpClientImpl {
    @Inject
    public FeeHttpClient(@Nullable Socks5ProxyProvider socks5ProxyProvider,
                         @Named(Config.ALLOW_LAN_FOR_HTTP_REQUESTS) boolean allowLanForHttpRequests,
                         @Named(Config.ALLOW_CLEARNET_HTTP_REQUESTS) boolean allowClearnetHttpRequests) {
        super(socks5ProxyProvider, allowLanForHttpRequests, allowClearnetHttpRequests);
    }
}

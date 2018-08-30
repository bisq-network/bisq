/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.httpapi;

import bisq.core.app.AppOptionKeys;

import bisq.common.app.AppModule;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;
import com.google.inject.name.Names;



import bisq.httpapi.service.HttpApiServer;
import bisq.httpapi.service.auth.TokenRegistry;
import bisq.httpapi.service.resources.ArbitratorEndpoint;
import bisq.httpapi.service.resources.BackupEndpoint;
import bisq.httpapi.service.resources.ClosedTradableEndpoint;
import bisq.httpapi.service.resources.MarketEndpoint;
import bisq.httpapi.service.resources.NetworkEndpoint;
import bisq.httpapi.service.resources.OfferEndpoint;
import bisq.httpapi.service.resources.PaymentAccountEndpoint;
import bisq.httpapi.service.resources.PreferencesEndpoint;
import bisq.httpapi.service.resources.TradeEndpoint;
import bisq.httpapi.service.resources.UserEndpoint;
import bisq.httpapi.service.resources.VersionEndpoint;
import bisq.httpapi.service.resources.WalletEndpoint;

public class HttpApiModule extends AppModule {

    public HttpApiModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(HttpApiServer.class).in(Singleton.class);
        bind(TokenRegistry.class).in(Singleton.class);

        bind(ArbitratorEndpoint.class).in(Singleton.class);
        bind(BackupEndpoint.class).in(Singleton.class);
        bind(ClosedTradableEndpoint.class).in(Singleton.class);
        bind(MarketEndpoint.class).in(Singleton.class);
        bind(NetworkEndpoint.class).in(Singleton.class);
        bind(OfferEndpoint.class).in(Singleton.class);
        bind(PaymentAccountEndpoint.class).in(Singleton.class);
        bind(PreferencesEndpoint.class).in(Singleton.class);
        bind(TradeEndpoint.class).in(Singleton.class);
        bind(UserEndpoint.class).in(Singleton.class);
        bind(VersionEndpoint.class).in(Singleton.class);
        bind(WalletEndpoint.class).in(Singleton.class);

        String httpApiHost = environment.getProperty(AppOptionKeys.HTTP_API_HOST, String.class, "127.0.0.1");
        bind(String.class).annotatedWith(Names.named(AppOptionKeys.HTTP_API_HOST)).toInstance(httpApiHost);

        Integer httpApiPort = Integer.valueOf(environment.getProperty(AppOptionKeys.HTTP_API_PORT, String.class, "8080"));
        bind(Integer.class).annotatedWith(Names.named(AppOptionKeys.HTTP_API_PORT)).toInstance(httpApiPort);
    }
}

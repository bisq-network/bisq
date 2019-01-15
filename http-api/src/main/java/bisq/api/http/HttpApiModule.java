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

package bisq.api.http;

import bisq.api.http.service.HttpApiServer;
import bisq.api.http.service.endpoint.VersionEndpoint;

import bisq.core.app.AppOptionKeys;

import bisq.common.app.AppModule;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

public class HttpApiModule extends AppModule {

    public HttpApiModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(HttpApiServer.class).in(Singleton.class);
        bind(VersionEndpoint.class).in(Singleton.class);

        String httpApiHost = environment.getProperty(AppOptionKeys.HTTP_API_HOST, String.class, "127.0.0.1");
        bind(String.class).annotatedWith(Names.named(AppOptionKeys.HTTP_API_HOST)).toInstance(httpApiHost);

        Integer httpApiPort = Integer.valueOf(environment.getProperty(AppOptionKeys.HTTP_API_PORT, String.class, "8080"));
        bind(Integer.class).annotatedWith(Names.named(AppOptionKeys.HTTP_API_PORT)).toInstance(httpApiPort);
    }
}

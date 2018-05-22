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

package network.bisq.api.app;

import bisq.common.app.AppModule;
import com.google.inject.Singleton;
import network.bisq.api.service.BisqApiApplication;
import network.bisq.api.service.TokenRegistry;
import org.springframework.core.env.Environment;

public class ApiModule extends AppModule {

    public ApiModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        // added for API usage
        bind(BisqApiApplication.class).in(Singleton.class);
        bind(MainViewModelHeadless.class).in(Singleton.class);
        bind(TokenRegistry.class).in(Singleton.class);
        bind(ApiEnvironment.class).toInstance((ApiEnvironment) environment);
    }
}

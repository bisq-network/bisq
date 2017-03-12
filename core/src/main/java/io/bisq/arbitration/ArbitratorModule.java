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

package io.bisq.arbitration;

import com.google.inject.Singleton;
import io.bisq.app.AppModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

public class ArbitratorModule extends AppModule {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorModule.class);

    public ArbitratorModule(Environment env) {
        super(env);
    }

    @Override
    protected final void configure() {
        bind(ArbitratorManager.class).in(Singleton.class);
        bind(DisputeManager.class).in(Singleton.class);
        bind(ArbitratorService.class).in(Singleton.class);
    }
}

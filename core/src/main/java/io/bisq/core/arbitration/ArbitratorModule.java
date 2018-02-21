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

package io.bisq.core.arbitration;

import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.bisq.common.app.AppModule;
import io.bisq.core.app.AppOptionKeys;
import org.springframework.core.env.Environment;

public class ArbitratorModule extends AppModule {
    public ArbitratorModule(Environment environment) {
        super(environment);
    }

    @Override
    protected final void configure() {
        Boolean useDevPrivilegeKeys = environment.getProperty(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS, Boolean.class, false);
        bind(boolean.class).annotatedWith(Names.named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS)).toInstance(useDevPrivilegeKeys);
        bind(ArbitratorManager.class).in(Singleton.class);
        bind(DisputeManager.class).in(Singleton.class);
        bind(ArbitratorService.class).in(Singleton.class);
    }
}

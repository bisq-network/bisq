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

package bisq.core.alert;

import bisq.core.app.AppOptionKeys;

import bisq.common.app.AppModule;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;

import static com.google.inject.name.Names.named;

public class AlertModule extends AppModule {
    public AlertModule(Environment environment) {
        super(environment);
    }

    @Override
    protected final void configure() {
        bind(AlertManager.class).in(Singleton.class);
        bind(PrivateNotificationManager.class).in(Singleton.class);
        bindConstant().annotatedWith(named(AppOptionKeys.IGNORE_DEV_MSG_KEY)).to(environment.getRequiredProperty(AppOptionKeys.IGNORE_DEV_MSG_KEY));
    }
}

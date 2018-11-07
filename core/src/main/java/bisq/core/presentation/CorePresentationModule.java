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

package bisq.core.presentation;

import bisq.common.app.AppModule;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;

public class CorePresentationModule extends AppModule {
    public CorePresentationModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(BalancePresentation.class).in(Singleton.class);
        bind(TradePresentation.class).in(Singleton.class);
        bind(DisputePresentation.class).in(Singleton.class);
    }
}


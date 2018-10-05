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

package bisq.monitor;

import bisq.monitor.metrics.p2p.MonitorP2PModule;

import bisq.core.app.BisqEnvironment;
import bisq.core.app.misc.ModuleForAppWithP2p;

import bisq.network.p2p.P2PModule;

import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

class MonitorModule extends ModuleForAppWithP2p {

    public MonitorModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        super.configure();

        bindConstant().annotatedWith(named(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL)).to(environment.getRequiredProperty(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL));
        bindConstant().annotatedWith(named(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL)).to(environment.getRequiredProperty(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL));
        bindConstant().annotatedWith(named(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL)).to(environment.getRequiredProperty(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL));
        bindConstant().annotatedWith(named(MonitorOptionKeys.PORT)).to(environment.getRequiredProperty(MonitorOptionKeys.PORT));
    }

    @Override
    protected void configEnvironment() {
        bind(BisqEnvironment.class).toInstance((MonitorEnvironment) environment);
    }

    @Override
    protected P2PModule p2pModule() {
        return new MonitorP2PModule(environment);
    }
}

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

package bisq.core.app.misc;

import bisq.common.app.Version;
import bisq.common.config.Config;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AppSetup {
    protected final Config config;

    @Inject
    public AppSetup(Config config) {
        // we need to reference it so the seed node stores tradeStatistics
        this.config = config;

        Version.setBaseCryptoNetworkId(this.config.baseCurrencyNetwork.ordinal());
        Version.printVersion();
    }

    public void start() {
        initPersistedDataHosts();
        initBasicServices();
    }

    abstract void initPersistedDataHosts();

    abstract void initBasicServices();
}

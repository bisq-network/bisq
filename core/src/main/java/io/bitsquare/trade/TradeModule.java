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

package io.bitsquare.trade;

import com.google.inject.Singleton;
import io.bitsquare.app.AppModule;
import io.bitsquare.app.CoreOptionKeys;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.statistics.TradeStatisticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

public class TradeModule extends AppModule {
    private static final Logger log = LoggerFactory.getLogger(TradeModule.class);

    public TradeModule(Environment env) {
        super(env);
    }

    @Override
    protected void configure() {
        bind(TradeManager.class).in(Singleton.class);
        bind(TradeStatisticsManager.class).in(Singleton.class);
        bind(ClosedTradableManager.class).in(Singleton.class);
        bind(FailedTradesManager.class).in(Singleton.class);
        bindConstant().annotatedWith(named(CoreOptionKeys.DUMP_STATISTICS)).to(env.getRequiredProperty(CoreOptionKeys.DUMP_STATISTICS));
    }
}

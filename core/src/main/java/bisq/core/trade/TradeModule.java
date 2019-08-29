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

package bisq.core.trade;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.sign.SignedWitnessStorageService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.account.witness.AccountAgeWitnessStorageService;
import bisq.core.app.AppOptionKeys;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.trade.statistics.AssetTradeActivityCheck;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatistics2StorageService;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.common.app.AppModule;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;

import static com.google.inject.name.Names.named;

public class TradeModule extends AppModule {

    public TradeModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(TradeManager.class).in(Singleton.class);
        bind(TradeStatisticsManager.class).in(Singleton.class);
        bind(TradeStatistics2StorageService.class).in(Singleton.class);
        bind(ClosedTradableManager.class).in(Singleton.class);
        bind(FailedTradesManager.class).in(Singleton.class);
        bind(AccountAgeWitnessService.class).in(Singleton.class);
        bind(AccountAgeWitnessStorageService.class).in(Singleton.class);
        bind(SignedWitnessService.class).in(Singleton.class);
        bind(SignedWitnessStorageService.class).in(Singleton.class);
        bind(ReferralIdService.class).in(Singleton.class);
        bind(AssetTradeActivityCheck.class).in(Singleton.class);
        bindConstant().annotatedWith(named(AppOptionKeys.DUMP_STATISTICS)).to(environment.getRequiredProperty(AppOptionKeys.DUMP_STATISTICS));
    }
}

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

package io.bisq.core.btc;

import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.bisq.common.app.AppModule;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.btc.wallet.*;
import io.bisq.core.provider.ProvidersRepository;
import io.bisq.core.provider.fee.FeeProvider;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.network.http.HttpClient;
import org.springframework.core.env.Environment;

import java.io.File;

import static com.google.inject.name.Names.named;

public class BitcoinModule extends AppModule {
    public BitcoinModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(RegTestHost.class).toInstance(environment.getProperty(BtcOptionKeys.REG_TEST_HOST, RegTestHost.class, RegTestHost.DEFAULT));

        bindConstant().annotatedWith(named(UserAgent.NAME_KEY)).to(environment.getRequiredProperty(UserAgent.NAME_KEY));
        bindConstant().annotatedWith(named(UserAgent.VERSION_KEY)).to(environment.getRequiredProperty(UserAgent.VERSION_KEY));
        bind(UserAgent.class).in(Singleton.class);

        File walletDir = new File(environment.getRequiredProperty(BtcOptionKeys.WALLET_DIR));
        bind(File.class).annotatedWith(named(BtcOptionKeys.WALLET_DIR)).toInstance(walletDir);

        bindConstant().annotatedWith(named(BtcOptionKeys.BTC_NODES)).to(environment.getRequiredProperty(BtcOptionKeys.BTC_NODES));
        bindConstant().annotatedWith(named(BtcOptionKeys.USE_TOR_FOR_BTC)).to(environment.getRequiredProperty(BtcOptionKeys.USE_TOR_FOR_BTC));
        String socks5DiscoverMode = environment.getProperty(BtcOptionKeys.SOCKS5_DISCOVER_MODE, String.class, "ALL");
        bind(String.class).annotatedWith(Names.named(BtcOptionKeys.SOCKS5_DISCOVER_MODE)).toInstance(socks5DiscoverMode);
        bindConstant().annotatedWith(named(AppOptionKeys.PROVIDERS)).to(environment.getRequiredProperty(AppOptionKeys.PROVIDERS));

        bind(AddressEntryList.class).in(Singleton.class);
        bind(WalletsSetup.class).in(Singleton.class);
        bind(BtcWalletService.class).in(Singleton.class);
        bind(BsqWalletService.class).in(Singleton.class);
        bind(TradeWalletService.class).in(Singleton.class);
        bind(BsqCoinSelector.class).in(Singleton.class);
        bind(BitcoinNodes.class).in(Singleton.class);

        bind(HttpClient.class).in(Singleton.class);
        bind(ProvidersRepository.class).in(Singleton.class);
        bind(FeeProvider.class).in(Singleton.class);
        bind(PriceFeedService.class).in(Singleton.class);
        bind(FeeService.class).in(Singleton.class);
    }
}


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

package bisq.core.btc;

import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.nodes.BtcNodes;
import bisq.core.btc.setup.RegTestHost;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqCoinSelector;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.NonBsqCoinSelector;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.provider.ProvidersRepository;
import bisq.core.provider.fee.FeeProvider;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;

import bisq.common.app.AppModule;
import bisq.common.config.Config;

import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.io.File;

import java.util.Arrays;
import java.util.List;

import static bisq.common.config.Config.PROVIDERS;
import static bisq.common.config.Config.WALLET_DIR;
import static com.google.inject.name.Names.named;

public class BitcoinModule extends AppModule {

    public BitcoinModule(Config config) {
        super(config);
    }

    @Override
    protected void configure() {
        // If we have selected BTC_DAO_REGTEST or BTC_DAO_TESTNET we use our master regtest node,
        // otherwise the specified host or default (localhost)
        String regTestHost = config.bitcoinRegtestHost;
        if (regTestHost.isEmpty()) {
            regTestHost = config.baseCurrencyNetwork.isDaoTestNet() ?
                    "104.248.31.39" :
                    config.baseCurrencyNetwork.isDaoRegTest() ?
                            "134.209.242.206" :
                            Config.DEFAULT_REGTEST_HOST;
        }

        RegTestHost.HOST = regTestHost;
        if (Arrays.asList("localhost", "127.0.0.1").contains(regTestHost)) {
            bind(RegTestHost.class).toInstance(RegTestHost.LOCALHOST);
        } else if ("none".equals(regTestHost)) {
            bind(RegTestHost.class).toInstance(RegTestHost.NONE);
        } else {
            bind(RegTestHost.class).toInstance(RegTestHost.REMOTE_HOST);
        }

        bind(File.class).annotatedWith(named(WALLET_DIR)).toInstance(config.walletDir);

        bindConstant().annotatedWith(named(Config.BTC_NODES)).to(config.btcNodes);
        bindConstant().annotatedWith(named(Config.USER_AGENT)).to(config.userAgent);
        bindConstant().annotatedWith(named(Config.NUM_CONNECTIONS_FOR_BTC)).to(config.numConnectionsForBtc);
        bindConstant().annotatedWith(named(Config.USE_ALL_PROVIDED_NODES)).to(config.useAllProvidedNodes);
        bindConstant().annotatedWith(named(Config.IGNORE_LOCAL_BTC_NODE)).to(config.ignoreLocalBtcNode);
        bindConstant().annotatedWith(named(Config.SOCKS5_DISCOVER_MODE)).to(config.socks5DiscoverMode);
        bind(new TypeLiteral<List<String>>(){}).annotatedWith(named(PROVIDERS)).toInstance(config.providers);

        bind(AddressEntryList.class).in(Singleton.class);
        bind(WalletsSetup.class).in(Singleton.class);
        bind(BtcWalletService.class).in(Singleton.class);
        bind(BsqWalletService.class).in(Singleton.class);
        bind(TradeWalletService.class).in(Singleton.class);
        bind(BsqCoinSelector.class).in(Singleton.class);
        bind(NonBsqCoinSelector.class).in(Singleton.class);
        bind(BtcNodes.class).in(Singleton.class);
        bind(Balances.class).in(Singleton.class);

        bind(ProvidersRepository.class).in(Singleton.class);
        bind(FeeProvider.class).in(Singleton.class);
        bind(PriceFeedService.class).in(Singleton.class);
        bind(FeeService.class).in(Singleton.class);
        bind(TxFeeEstimationService.class).in(Singleton.class);
    }
}


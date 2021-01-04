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

package bisq.core.app;

import bisq.core.alert.AlertModule;
import bisq.core.btc.BitcoinModule;
import bisq.core.dao.DaoModule;
import bisq.core.filter.FilterModule;
import bisq.core.network.CoreNetworkFilter;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.offer.OfferModule;
import bisq.core.presentation.CorePresentationModule;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.trade.TradeModule;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import bisq.network.crypto.EncryptionServiceModule;
import bisq.network.p2p.P2PModule;
import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.network.NetworkFilter;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.app.AppModule;
import bisq.common.config.Config;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.PubKeyRingProvider;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import com.google.inject.Singleton;

import java.io.File;

import static bisq.common.config.Config.*;
import static com.google.inject.name.Names.named;

public class CoreModule extends AppModule {

    public CoreModule(Config config) {
        super(config);
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(config);

        bind(BridgeAddressProvider.class).to(Preferences.class);

        bind(SeedNodeRepository.class).to(DefaultSeedNodeRepository.class);
        bind(NetworkFilter.class).to(CoreNetworkFilter.class).in(Singleton.class);

        bind(File.class).annotatedWith(named(STORAGE_DIR)).toInstance(config.storageDir);

        CoinFormatter btcFormatter = new ImmutableCoinFormatter(config.networkParameters.getMonetaryFormat());
        bind(CoinFormatter.class).annotatedWith(named(FormattingUtils.BTC_FORMATTER_KEY)).toInstance(btcFormatter);

        bind(File.class).annotatedWith(named(KEY_STORAGE_DIR)).toInstance(config.keyStorageDir);

        bind(NetworkProtoResolver.class).to(CoreNetworkProtoResolver.class);
        bind(PersistenceProtoResolver.class).to(CorePersistenceProtoResolver.class);

        bindConstant().annotatedWith(named(USE_DEV_PRIVILEGE_KEYS)).to(config.useDevPrivilegeKeys);
        bindConstant().annotatedWith(named(USE_DEV_MODE)).to(config.useDevMode);
        bindConstant().annotatedWith(named(USE_DEV_MODE_HEADER)).to(config.useDevModeHeader);
        bindConstant().annotatedWith(named(REFERRAL_ID)).to(config.referralId);

        // ordering is used for shut down sequence
        install(new TradeModule(config));
        install(new EncryptionServiceModule(config));
        install(new OfferModule(config));
        install(new P2PModule(config));
        install(new BitcoinModule(config));
        install(new DaoModule(config));
        install(new AlertModule(config));
        install(new FilterModule(config));
        install(new CorePresentationModule(config));
        bind(PubKeyRing.class).toProvider(PubKeyRingProvider.class);
    }
}

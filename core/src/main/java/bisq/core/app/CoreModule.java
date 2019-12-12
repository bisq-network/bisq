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
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.app.AppModule;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.PubKeyRingProvider;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import org.springframework.core.env.Environment;

import java.io.File;

import static bisq.common.config.Config.*;
import static com.google.inject.name.Names.named;

public class CoreModule extends AppModule {

    public CoreModule(Environment environment, Config config) {
        super(environment, config);
    }

    @Override
    protected void configure() {
        bind(BisqEnvironment.class).toInstance((BisqEnvironment) environment);
        bind(Config.class).toInstance(config);

        bind(BridgeAddressProvider.class).to(Preferences.class);

        bind(SeedNodeRepository.class).to(DefaultSeedNodeRepository.class);

        bind(File.class).annotatedWith(named(STORAGE_DIR)).toInstance(config.getStorageDir());

        CoinFormatter btcFormatter = new ImmutableCoinFormatter(BaseCurrencyNetwork.CURRENT_PARAMETERS.getMonetaryFormat());
        bind(CoinFormatter.class).annotatedWith(named(FormattingUtils.BTC_FORMATTER_KEY)).toInstance(btcFormatter);

        bind(File.class).annotatedWith(named(KEY_STORAGE_DIR)).toInstance(config.getKeyStorageDir());

        bind(NetworkProtoResolver.class).to(CoreNetworkProtoResolver.class);
        bind(PersistenceProtoResolver.class).to(CorePersistenceProtoResolver.class);

        bind(boolean.class).annotatedWith(named(USE_DEV_PRIVILEGE_KEYS)).toInstance(config.isUseDevPrivilegeKeys());
        bind(boolean.class).annotatedWith(named(USE_DEV_MODE)).toInstance(config.isUseDevMode());
        bind(String.class).annotatedWith(named(REFERRAL_ID)).toInstance(config.getReferralId());


        // ordering is used for shut down sequence
        install(new TradeModule(environment, config));
        install(new EncryptionServiceModule(environment, config));
        install(new OfferModule(environment, config));
        install(new P2PModule(environment, config));
        install(new BitcoinModule(environment, config));
        install(new DaoModule(environment, config));
        install(new AlertModule(environment, config));
        install(new FilterModule(environment, config));
        install(new CorePresentationModule(environment, config));
        bind(PubKeyRing.class).toProvider(PubKeyRingProvider.class);
    }
}

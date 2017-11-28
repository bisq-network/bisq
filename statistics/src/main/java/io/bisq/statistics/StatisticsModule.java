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

package io.bisq.statistics;

import com.google.inject.Singleton;
import io.bisq.common.Clock;
import io.bisq.common.app.AppModule;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.alert.AlertModule;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.arbitration.ArbitratorModule;
import io.bisq.core.btc.BitcoinModule;
import io.bisq.core.dao.DaoModule;
import io.bisq.core.filter.FilterModule;
import io.bisq.core.network.CoreSeedNodesRepository;
import io.bisq.core.offer.OfferModule;
import io.bisq.core.proto.network.CoreNetworkProtoResolver;
import io.bisq.core.proto.persistable.CorePersistenceProtoResolver;
import io.bisq.core.trade.TradeModule;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.network.crypto.EncryptionServiceModule;
import io.bisq.network.p2p.P2PModule;
import io.bisq.network.p2p.network.BridgeAddressProvider;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import org.springframework.core.env.Environment;

import java.io.File;

import static com.google.inject.name.Names.named;

class StatisticsModule extends AppModule {

    public StatisticsModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(BisqEnvironment.class).toInstance((BisqEnvironment) environment);

        // bind(CachingViewLoader.class).in(Singleton.class);
        bind(KeyStorage.class).in(Singleton.class);
        bind(KeyRing.class).in(Singleton.class);
        bind(User.class).in(Singleton.class);
        // bind(NotificationCenter.class).in(Singleton.class);
        bind(Clock.class).in(Singleton.class);
        bind(NetworkProtoResolver.class).to(CoreNetworkProtoResolver.class).in(Singleton.class);
        bind(PersistenceProtoResolver.class).to(CorePersistenceProtoResolver.class).in(Singleton.class);
        bind(Preferences.class).in(Singleton.class);
        bind(BridgeAddressProvider.class).to(Preferences.class).in(Singleton.class);

        bind(SeedNodesRepository.class).to(CoreSeedNodesRepository.class).in(Singleton.class);

        File storageDir = new File(environment.getRequiredProperty(Storage.STORAGE_DIR));
        bind(File.class).annotatedWith(named(Storage.STORAGE_DIR)).toInstance(storageDir);

        File keyStorageDir = new File(environment.getRequiredProperty(KeyStorage.KEY_STORAGE_DIR));
        bind(File.class).annotatedWith(named(KeyStorage.KEY_STORAGE_DIR)).toInstance(keyStorageDir);


        // ordering is used for shut down sequence
        install(tradeModule());
        install(encryptionServiceModule());
        install(arbitratorModule());
        install(offerModule());
        install(torModule());
        install(bitcoinModule());
        install(daoModule());
        //install(guiModule());
        install(alertModule());
        install(filterModule());
    }

    private TradeModule tradeModule() {
        return new TradeModule(environment);
    }

    private EncryptionServiceModule encryptionServiceModule() {
        return new EncryptionServiceModule(environment);
    }

    private ArbitratorModule arbitratorModule() {
        return new ArbitratorModule(environment);
    }

    private AlertModule alertModule() {
        return new AlertModule(environment);
    }

    private FilterModule filterModule() {
        return new FilterModule(environment);
    }

    private OfferModule offerModule() {
        return new OfferModule(environment);
    }

    private P2PModule torModule() {
        return new P2PModule(environment);
    }

    private BitcoinModule bitcoinModule() {
        return new BitcoinModule(environment);
    }

    private DaoModule daoModule() {
        return new DaoModule(environment);
    }

}

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

package bisq.desktop.app;

import bisq.desktop.DesktopModule;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.main.overlays.notifications.NotificationCenter;

import bisq.core.alert.AlertModule;
import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;
import bisq.core.arbitration.ArbitratorModule;
import bisq.core.btc.BitcoinModule;
import bisq.core.dao.DaoModule;
import bisq.core.filter.FilterModule;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.network.p2p.seed.SeedNodeAddressLookup;
import bisq.core.offer.OfferModule;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.trade.TradeModule;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.crypto.EncryptionServiceModule;
import bisq.network.p2p.P2PModule;
import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.Clock;
import bisq.common.app.AppModule;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.Storage;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

import javafx.stage.Stage;

import java.io.File;

import static com.google.inject.name.Names.named;

public class BisqAppModule extends AppModule {

    private final Stage primaryStage;

    public BisqAppModule(Environment environment, Stage primaryStage) {
        super(environment);
        this.primaryStage = primaryStage;
    }

    @Override
    protected void configure() {
        bind(BisqEnvironment.class).toInstance((BisqEnvironment) environment);

        bind(CachingViewLoader.class).in(Singleton.class);
        bind(KeyStorage.class).in(Singleton.class);
        bind(KeyRing.class).in(Singleton.class);
        bind(User.class).in(Singleton.class);
        bind(NotificationCenter.class).in(Singleton.class);
        bind(Clock.class).in(Singleton.class);
        bind(Preferences.class).in(Singleton.class);
        bind(BridgeAddressProvider.class).to(Preferences.class).in(Singleton.class);

        bind(SeedNodeAddressLookup.class).in(Singleton.class);
        bind(SeedNodeRepository.class).to(DefaultSeedNodeRepository.class).in(Singleton.class);

        File storageDir = new File(environment.getRequiredProperty(Storage.STORAGE_DIR));
        bind(File.class).annotatedWith(named(Storage.STORAGE_DIR)).toInstance(storageDir);

        File keyStorageDir = new File(environment.getRequiredProperty(KeyStorage.KEY_STORAGE_DIR));
        bind(File.class).annotatedWith(named(KeyStorage.KEY_STORAGE_DIR)).toInstance(keyStorageDir);

        bind(NetworkProtoResolver.class).to(CoreNetworkProtoResolver.class).in(Singleton.class);
        bind(PersistenceProtoResolver.class).to(CorePersistenceProtoResolver.class).in(Singleton.class);

        Boolean useDevPrivilegeKeys = environment.getProperty(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS, Boolean.class, false);
        bind(boolean.class).annotatedWith(Names.named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS)).toInstance(useDevPrivilegeKeys);

        Boolean useDevMode = environment.getProperty(AppOptionKeys.USE_DEV_MODE, Boolean.class, false);
        bind(boolean.class).annotatedWith(Names.named(AppOptionKeys.USE_DEV_MODE)).toInstance(useDevMode);

        // ordering is used for shut down sequence
        install(tradeModule());
        install(encryptionServiceModule());
        install(arbitratorModule());
        install(offerModule());
        install(p2pModule());
        install(bitcoinModule());
        install(daoModule());
        install(guiModule());
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

    private P2PModule p2pModule() {
        return new P2PModule(environment);
    }

    private BitcoinModule bitcoinModule() {
        return new BitcoinModule(environment);
    }

    private DaoModule daoModule() {
        return new DaoModule(environment);
    }

    private DesktopModule guiModule() {
        return new DesktopModule(environment, primaryStage);
    }
}

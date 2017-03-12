/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.app;

import com.google.inject.Singleton;
import io.bisq.alert.AlertModule;
import io.bisq.arbitration.ArbitratorModule;
import io.bisq.btc.BitcoinModule;
import io.bisq.common.Clock;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.crypto.EncryptionServiceModule;
import io.bisq.dao.DaoModule;
import io.bisq.filter.FilterModule;
import io.bisq.gui.GuiModule;
import io.bisq.gui.common.view.CachingViewLoader;
import io.bisq.gui.main.overlays.notifications.NotificationCenter;
import io.bisq.messages.user.Preferences;
import io.bisq.p2p.P2PModule;
import io.bisq.storage.Storage;
import io.bisq.trade.TradeModule;
import io.bisq.trade.offer.OfferModule;
import io.bisq.user.User;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.io.File;

import static com.google.inject.name.Names.named;

class BitsquareAppModule extends AppModule {
    private static final Logger log = LoggerFactory.getLogger(BitsquareAppModule.class);

    private final Stage primaryStage;

    public BitsquareAppModule(Environment env, Stage primaryStage) {
        super(env);
        this.primaryStage = primaryStage;
    }

    @Override
    protected void configure() {
        bind(CachingViewLoader.class).in(Singleton.class);
        bind(KeyStorage.class).in(Singleton.class);
        bind(KeyRing.class).in(Singleton.class);
        bind(User.class).in(Singleton.class);
        bind(Preferences.class).in(Singleton.class);
        bind(NotificationCenter.class).in(Singleton.class);
        bind(Clock.class).in(Singleton.class);

        File storageDir = new File(env.getRequiredProperty(Storage.DIR_KEY));
        bind(File.class).annotatedWith(named(Storage.DIR_KEY)).toInstance(storageDir);

        File keyStorageDir = new File(env.getRequiredProperty(KeyStorage.DIR_KEY));
        bind(File.class).annotatedWith(named(KeyStorage.DIR_KEY)).toInstance(keyStorageDir);

        bind(BitsquareEnvironment.class).toInstance((BitsquareEnvironment) env);

        // ordering is used for shut down sequence
        install(tradeModule());
        install(encryptionServiceModule());
        install(arbitratorModule());
        install(offerModule());
        install(torModule());
        install(bitcoinModule());
        install(daoModule());
        install(guiModule());
        install(alertModule());
        install(filterModule());
    }

    private TradeModule tradeModule() {
        return new TradeModule(env);
    }

    private EncryptionServiceModule encryptionServiceModule() {
        return new EncryptionServiceModule(env);
    }

    private ArbitratorModule arbitratorModule() {
        return new ArbitratorModule(env);
    }

    private AlertModule alertModule() {
        return new AlertModule(env);
    }

    private FilterModule filterModule() {
        return new FilterModule(env);
    }

    private OfferModule offerModule() {
        return new OfferModule(env);
    }

    private P2PModule torModule() {
        return new P2PModule(env);
    }

    private BitcoinModule bitcoinModule() {
        return new BitcoinModule(env);
    }

    private DaoModule daoModule() {
        return new DaoModule(env);
    }

    private GuiModule guiModule() {
        return new GuiModule(env, primaryStage);
    }
}

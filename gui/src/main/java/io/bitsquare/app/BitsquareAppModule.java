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

package io.bitsquare.app;

import com.google.inject.Singleton;
import io.bitsquare.alert.AlertModule;
import io.bitsquare.arbitration.ArbitratorModule;
import io.bitsquare.btc.BitcoinModule;
import io.bitsquare.common.Clock;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.KeyStorage;
import io.bitsquare.crypto.EncryptionServiceModule;
import io.bitsquare.filter.FilterModule;
import io.bitsquare.gui.GuiModule;
import io.bitsquare.gui.common.view.CachingViewLoader;
import io.bitsquare.gui.main.overlays.notifications.NotificationCenter;
import io.bitsquare.p2p.P2PModule;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.TradeModule;
import io.bitsquare.trade.offer.OfferModule;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
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

    private GuiModule guiModule() {
        return new GuiModule(env, primaryStage);
    }
}

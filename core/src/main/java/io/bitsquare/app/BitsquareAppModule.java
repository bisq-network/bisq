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

import io.bitsquare.BitsquareModule;
import io.bitsquare.arbitration.ArbitratorModule;
import io.bitsquare.arbitration.tomp2p.TomP2PArbitratorModule;
import io.bitsquare.btc.BitcoinModule;
import io.bitsquare.crypto.CryptoModule;
import io.bitsquare.gui.GuiModule;
import io.bitsquare.offer.OfferModule;
import io.bitsquare.offer.tomp2p.TomP2POfferModule;
import io.bitsquare.p2p.P2PModule;
import io.bitsquare.p2p.tomp2p.TomP2PModule;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.TradeModule;
import io.bitsquare.user.AccountSettings;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;

import com.google.inject.Injector;
import com.google.inject.Singleton;

import java.io.File;

import javafx.stage.Stage;

import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

class BitsquareAppModule extends BitsquareModule {

    private final Stage primaryStage;

    public BitsquareAppModule(Environment env, Stage primaryStage) {
        super(env);
        this.primaryStage = primaryStage;
    }

    @Override
    protected void configure() {
        bind(User.class).in(Singleton.class);
        bind(Preferences.class).in(Singleton.class);
        bind(AccountSettings.class).in(Singleton.class);

        File storageDir = new File(env.getRequiredProperty(Storage.DIR_KEY));
        bind(File.class).annotatedWith(named(Storage.DIR_KEY)).toInstance(storageDir);
        
        bind(Environment.class).toInstance(env);
        bind(UpdateProcess.class).in(Singleton.class);
        
        install(networkModule());
        install(bitcoinModule());
        install(cryptoModule());
        install(tradeMessageModule());
        install(offerModule());
        install(arbitratorMessageModule());
        install(guiModule());
    }

    protected ArbitratorModule arbitratorMessageModule() {
        return new TomP2PArbitratorModule(env);
    }

    protected P2PModule networkModule() {
        return new TomP2PModule(env);
    }

    protected BitcoinModule bitcoinModule() {
        return new BitcoinModule(env);
    }

    protected CryptoModule cryptoModule() {
        return new CryptoModule(env);
    }

    protected TradeModule tradeMessageModule() {
        return new TradeModule(env);
    }

    protected OfferModule offerModule() {
        return new TomP2POfferModule(env);
    }

    protected GuiModule guiModule() {
        return new GuiModule(env, primaryStage);
    }

    @Override
    protected void doClose(Injector injector) {
    }
}
